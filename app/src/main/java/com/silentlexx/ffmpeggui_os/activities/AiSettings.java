package com.silentlexx.ffmpeggui_os.activities;

import android.os.Bundle;
import android.view.View;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.EditText;
import android.widget.Spinner;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;

import com.silentlexx.ffmpeggui_os.R;
import com.silentlexx.ffmpeggui_os.ai.AiCommandClient;
import com.silentlexx.ffmpeggui_os.ai.AiConfig;
import com.silentlexx.ffmpeggui_os.ai.SecureApiKeyStore;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class AiSettings extends AppCompatActivity {

    private EditText endpointView;
    private EditText apiKeyView;
    private EditText customModelView;
    private Spinner modelView;
    private TextView keyStatusView;
    private TextView modelStatusView;
    private View clearKeyView;
    private Button refreshModelsView;
    private AiConfig aiConfig;
    private SecureApiKeyStore keyStore;
    private final ExecutorService modelExecutor = Executors.newSingleThreadExecutor();
    private final List<String> modelOptions = new ArrayList<>();
    private int customModelPosition;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.ai_settings);
        EdgeToEdgeContentInsets.install(this);
        setTitle(R.string.ai_settings_title);
        if (getSupportActionBar() != null) {
            getSupportActionBar().setDisplayHomeAsUpEnabled(true);
        }

        aiConfig = new AiConfig(this);
        keyStore = new SecureApiKeyStore(this);
        endpointView = findViewById(R.id.ai_endpoint);
        apiKeyView = findViewById(R.id.ai_api_key);
        customModelView = findViewById(R.id.ai_custom_model);
        modelView = findViewById(R.id.ai_model);
        keyStatusView = findViewById(R.id.ai_key_status);
        modelStatusView = findViewById(R.id.ai_model_status);
        refreshModelsView = findViewById(R.id.ai_refresh_models);
        clearKeyView = findViewById(R.id.ai_clear_key);

        endpointView.setText(aiConfig.getEndpoint());
        setupModels(aiConfig.getModel());
        updateKeyStatus();

        findViewById(R.id.ai_save).setOnClickListener(view -> save());
        refreshModelsView.setOnClickListener(view -> refreshModels());
        clearKeyView.setOnClickListener(view -> confirmClearKey());
        if (keyStore.hasKey()) {
            refreshModels();
        } else {
            modelStatusView.setText(R.string.ai_models_save_key_first);
        }
    }

    private void setupModels(String savedModel) {
        modelOptions.clear();
        if (savedModel != null && !savedModel.trim().isEmpty()) {
            modelOptions.add(savedModel.trim());
        }
        rebuildModelAdapter(savedModel);
    }

    private void rebuildModelAdapter(String selectedModel) {
        List<String> labels = new ArrayList<>(modelOptions);
        customModelPosition = labels.size();
        labels.add(AiConfig.CUSTOM_MODEL_LABEL);
        ArrayAdapter<String> adapter = new ArrayAdapter<>(this,
                android.R.layout.simple_spinner_item, labels);
        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        modelView.setAdapter(adapter);

        int position = modelOptions.indexOf(selectedModel);
        if (position >= 0) {
            modelView.setSelection(position);
            customModelView.setText("");
        } else {
            modelView.setSelection(customModelPosition);
            customModelView.setText(selectedModel == null ? "" : selectedModel);
        }
        setCustomModelVisible(position < 0);
        modelView.setOnItemSelectedListener(new android.widget.AdapterView.OnItemSelectedListener() {
            @Override
            public void onItemSelected(android.widget.AdapterView<?> parent, View view,
                                       int position, long id) {
                setCustomModelVisible(position == customModelPosition);
            }

            @Override
            public void onNothingSelected(android.widget.AdapterView<?> parent) {
                setCustomModelVisible(false);
            }
        });
    }

    private void setCustomModelVisible(boolean visible) {
        customModelView.setVisibility(visible ? View.VISIBLE : View.GONE);
    }

    private String selectedModel() {
        int position = modelView.getSelectedItemPosition();
        if (position >= 0 && position < modelOptions.size()) {
            return modelOptions.get(position);
        }
        return customModelView.getText().toString().trim();
    }

    private void refreshModels() {
        final String endpoint = endpointView.getText().toString().trim();
        final String savedModel = selectedModel();
        String endpointError = AiConfig.validateEndpoint(endpoint);
        if (endpointError != null) {
            showFieldError(endpointView, endpointError);
            return;
        }
        if (AiConfig.isCleartextEndpoint(endpoint)
                && !aiConfig.isEndpointApproved(endpoint)) {
            modelStatusView.setText(R.string.ai_cleartext_confirmation_required);
            return;
        }
        final String enteredKey = apiKeyView.getText().toString().trim();
        refreshModelsView.setEnabled(false);
        modelStatusView.setText(R.string.ai_models_loading);
        modelExecutor.execute(() -> {
            try {
                String apiKey = enteredKey;
                if (apiKey.isEmpty()) {
                    if (!keyStore.hasKey()) {
                        throw new AiCommandClient.AiException(
                                getString(R.string.ai_models_save_key_first));
                    }
                    apiKey = keyStore.read();
                }
                List<String> discovered = new AiCommandClient().listModels(endpoint, apiKey);
                final String selected = savedModel;
                final List<String> result = new ArrayList<>(discovered);
                runOnUiThread(() -> {
                    if (isFinishing() || (android.os.Build.VERSION.SDK_INT >= 17 && isDestroyed())) {
                        return;
                    }
                    modelOptions.clear();
                    modelOptions.addAll(result);
                    rebuildModelAdapter(selected);
                    modelStatusView.setText(getString(R.string.ai_models_loaded, result.size()));
                    refreshModelsView.setEnabled(true);
                });
            } catch (Exception e) {
                final String message = e.getMessage() == null
                        ? getString(R.string.ai_models_load_failed, "unknown error")
                        : e.getMessage();
                runOnUiThread(() -> {
                    if (isFinishing() || (android.os.Build.VERSION.SDK_INT >= 17 && isDestroyed())) {
                        return;
                    }
                    modelStatusView.setText(getString(R.string.ai_models_load_failed, message));
                    refreshModelsView.setEnabled(true);
                });
            }
        });
    }

    private void save() {
        String endpoint = endpointView.getText().toString().trim();
        String model = selectedModel();
        String error = AiConfig.validateEndpoint(endpoint);
        if (error != null) {
            showFieldError(endpointView, error);
            return;
        }
        error = AiConfig.validateModel(model);
        if (error != null) {
            showFieldError(customModelView.getVisibility() == View.VISIBLE
                    ? customModelView : endpointView, error);
            return;
        }

        String enteredKey = apiKeyView.getText().toString().trim();
        if (enteredKey.isEmpty() && !keyStore.hasKey()) {
            showFieldError(apiKeyView, getString(R.string.ai_key_required));
            return;
        }
        if (AiConfig.isCleartextEndpoint(endpoint) &&
                !aiConfig.isEndpointApproved(endpoint)) {
            showCleartextWarning(endpoint, model, enteredKey);
            return;
        }
        persistSettings(endpoint, model, enteredKey,
                AiConfig.isCleartextEndpoint(endpoint));
    }

    private void showCleartextWarning(String endpoint, String model, String enteredKey) {
        new AlertDialog.Builder(this)
                .setTitle(R.string.ai_cleartext_warning_title)
                .setMessage(R.string.ai_cleartext_warning_message)
                .setPositiveButton(R.string.ai_cleartext_confirm,
                        (dialog, which) -> persistSettings(endpoint, model, enteredKey, true))
                .setNegativeButton(R.string.cancel, null)
                .show();
    }

    private void persistSettings(String endpoint, String model, String enteredKey,
                                 boolean cleartextAcknowledged) {
        try {
            if (!enteredKey.isEmpty()) {
                keyStore.save(enteredKey);
                apiKeyView.setText("");
            }
            aiConfig.save(endpoint, model, cleartextAcknowledged);
            Toast.makeText(this, R.string.ai_settings_saved, Toast.LENGTH_SHORT).show();
            setResult(RESULT_OK);
            finish();
        } catch (Exception e) {
            showError(getString(R.string.ai_key_save_error));
        }
    }

    private void confirmClearKey() {
        if (!keyStore.hasKey()) {
            updateKeyStatus();
            return;
        }
        new AlertDialog.Builder(this)
                .setTitle(R.string.ai_clear_key)
                .setMessage(R.string.ai_clear_key_confirm)
                .setPositiveButton(R.string.delete, (dialog, which) -> clearKey())
                .setNegativeButton(R.string.cancel, null)
                .show();
    }

    private void clearKey() {
        try {
            keyStore.clear();
            apiKeyView.setText("");
            updateKeyStatus();
        } catch (Exception e) {
            showError(getString(R.string.ai_key_clear_error));
        }
    }

    private void updateKeyStatus() {
        boolean hasKey = keyStore.hasKey();
        keyStatusView.setText(hasKey
                ? R.string.ai_key_saved_securely
                : R.string.ai_key_not_saved);
        clearKeyView.setEnabled(hasKey);
        clearKeyView.setAlpha(hasKey ? 1f : 0.5f);
    }

    private void showFieldError(EditText field, String message) {
        field.setError(message);
        field.requestFocus();
    }

    private void showError(String message) {
        new AlertDialog.Builder(this)
                .setTitle(R.string.err)
                .setMessage(message)
                .setPositiveButton(android.R.string.ok, null)
                .show();
    }

    @Override
    public boolean onSupportNavigateUp() {
        finish();
        return true;
    }

    @Override
    protected void onDestroy() {
        modelExecutor.shutdownNow();
        super.onDestroy();
    }
}
