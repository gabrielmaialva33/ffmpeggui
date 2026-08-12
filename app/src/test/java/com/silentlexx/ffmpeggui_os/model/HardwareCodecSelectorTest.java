package com.silentlexx.ffmpeggui_os.model;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import android.media.MediaCodecInfo;

import org.junit.Test;

public class HardwareCodecSelectorTest {

    @Test
    public void rejectsKnownReferenceAndSoftwareComponents() {
        assertTrue(HardwareCodecSelector.isKnownSoftwareComponentName(null));
        assertTrue(HardwareCodecSelector.isKnownSoftwareComponentName(
                "c2.android.av1.encoder"));
        assertTrue(HardwareCodecSelector.isKnownSoftwareComponentName(
                "OMX.google.vp9.encoder"));
        assertTrue(HardwareCodecSelector.isKnownSoftwareComponentName(
                "vendor.codec.sw.encoder"));
        assertTrue(HardwareCodecSelector.isKnownSoftwareComponentName(
                "vendor.software.encoder"));
    }

    @Test
    public void preservesFrameworkReportedTensorAndQualcommHardwareNames() {
        for (String component : new String[] {
                "c2.google.av1.encoder",
                "c2.qti.avc.encoder",
                "OMX.qcom.video.encoder.hevc",
                "c2.exynos.av1.encoder",
                "OMX.SEC.avc.encoder",
                "c2.mtk.hevc.encoder",
                "OMX.MTK.VIDEO.ENCODER.VP9"
        }) {
            assertFalse(component,
                    HardwareCodecSelector.isKnownSoftwareComponentName(component));
            assertTrue(component,
                    HardwareCodecSelector.isUsableHardwareComponentName(component, true));
            assertFalse(component,
                    HardwareCodecSelector.isUsableHardwareComponentName(component, false));
        }
    }

    @Test
    public void deniesReferenceSoftwareEvenWhenFrameworkFlagIsWrong() {
        assertFalse(HardwareCodecSelector.isUsableHardwareComponentName(
                "c2.android.av1.encoder", true));
        assertFalse(HardwareCodecSelector.isUsableHardwareComponentName(
                "OMX.google.vp9.encoder", true));
    }

    @Test
    public void acceptsOnlyFrameworkOrKnownSystemSoftwareComponents() {
        assertTrue(HardwareCodecSelector.isUsableSoftwareComponentName(
                "c2.android.av1.encoder", false));
        assertTrue(HardwareCodecSelector.isUsableSoftwareComponentName(
                "OMX.google.vp9.encoder", false));
        assertTrue(HardwareCodecSelector.isUsableSoftwareComponentName(
                "vendor.reference.encoder", true));

        assertFalse(HardwareCodecSelector.isUsableSoftwareComponentName(
                "c2.google.av1.encoder", false));
        assertFalse(HardwareCodecSelector.isUsableSoftwareComponentName(
                "c2.qti.avc.encoder", false));
        assertFalse(HardwareCodecSelector.isUsableSoftwareComponentName(null, true));
    }

    @Test
    public void classifiesPreQGoogleCodec2AsSystemSoftwareOnly() {
        assertTrue(HardwareCodecSelector.isLegacySystemSoftwareComponentName(
                "c2.google.avc.encoder"));
        assertTrue(HardwareCodecSelector.isLegacySystemSoftwareComponentName(
                "c2.android.av1.encoder"));
        assertFalse(HardwareCodecSelector.isLegacySystemSoftwareComponentName(
                "c2.exynos.avc.encoder"));
        assertFalse(HardwareCodecSelector.isLegacySystemSoftwareComponentName(
                "OMX.MTK.VIDEO.ENCODER.HEVC"));
    }

    @Test
    public void acceptsPlanarOrFlexibleInputAndRejectsSemiplanarOnlyEncoder() {
        assertTrue(HardwareCodecSelector.supportsPlanarEncoderInput(new int[]{
                MediaCodecInfo.CodecCapabilities.COLOR_FormatYUV420Planar
        }));
        assertTrue(HardwareCodecSelector.supportsPlanarEncoderInput(new int[]{
                MediaCodecInfo.CodecCapabilities.COLOR_FormatYUV420Flexible
        }));
        assertFalse(HardwareCodecSelector.supportsPlanarEncoderInput(new int[]{
                MediaCodecInfo.CodecCapabilities.COLOR_FormatYUV420SemiPlanar
        }));
        assertFalse(HardwareCodecSelector.supportsPlanarEncoderInput(new int[0]));
        assertFalse(HardwareCodecSelector.supportsPlanarEncoderInput(null));
    }
}
