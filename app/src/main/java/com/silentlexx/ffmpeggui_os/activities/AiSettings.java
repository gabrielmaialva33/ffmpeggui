package com.silentlexx.ffmpeggui_os.activities;

import android.os.Bundle;
import android.view.View;
import android.widget.ArrayAdapter;
import android.widget.EditText;
import android.widget.Spinner;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;

import com.silentlexx.ffmpeggui_os.R;
import com.silentlexx.ffmpeggui_os.ai.AiConfig;
import com.silentlexx.ffmpeggui_os.ai.SecureApiKeyStore;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

public class AiSettings extends AppCompatActivity {

    private EditText endpointView;
    private EditText apiKeyView;
    private EditText customModelView;
    private Spinner modelView;
    private TextView keyStatusView;
    private AiConfig aiConfig;
    private SecureApiKeyStore keyStore;

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

        endpointView.setText(aiConfig.getEndpoint());
        setupModels(aiConfig.getModel());
        updateKeyStatus();

        findViewById(R.id.ai_save).setOnClickListener(view -> save());
        findViewById(R.id.ai_clear_key).setOnClickListener(view -> confirmClearKey());
    }

    private void setupModels(String savedModel) {
        List<String> labels = new ArrayList<>(Arrays.asList(AiConfig.PRESET_MODELS));
        labels.add(AiConfig.CUSTOM_MODEL_LABEL);
        ArrayAdapter<String> adapter = new ArrayAdapter<>(this,
                android.R.layout.simple_spinner_item, labels);
        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        modelView.setAdapter(adapter);

        int position = AiConfig.findPresetPosition(savedModel);
        modelView.setSelection(position);
        if (position == AiConfig.PRESET_MODELS.length) {
            customModelView.setText(savedModel);
        }
        setCustomModelVisible(position == AiConfig.PRESET_MODELS.length);
        modelView.setOnItemSelectedListener(new android.widget.AdapterView.OnItemSelectedListener() {
            @Override
            public void onItemSelected(android.widget.AdapterView<?> parent, View view,
                                       int position, long id) {
                setCustomModelVisible(position == AiConfig.PRESET_MODELS.length);
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
        if (position >= 0 && position < AiConfig.PRESET_MODELS.length) {
            return AiConfig.PRESET_MODELS[position];
        }
        return customModelView.getText().toString().trim();
    }

    private void save() {
        String endpoint = endpointView.getText().toString().trim();
        String model = selectedModel();
        String error = AiConfig.validateEndpoint(endpoint);
        if (error == null) {
            error = AiConfig.validateModel(model);
        }
        if (error != null) {
            showError(error);
            return;
        }

        String enteredKey = apiKeyView.getText().toString().trim();
        if (enteredKey.isEmpty() && !keyStore.hasKey()) {
            showError(getString(R.string.ai_key_required));
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
        keyStatusView.setText(keyStore.hasKey()
                ? R.string.ai_key_saved_securely
                : R.string.ai_key_not_saved);
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
}
