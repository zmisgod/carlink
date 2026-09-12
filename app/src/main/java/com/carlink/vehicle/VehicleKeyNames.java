package com.carlink.vehicle;

import android.view.KeyEvent;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.TreeSet;

/** Vendor codes from the user's GInputBridge KeyCode.java; unknown codes stay visible. */
public final class VehicleKeyNames {
    private static final Map<Integer, String> VENDOR = new LinkedHashMap<>();
    static {
        VENDOR.put(300003, "KEYCODE_DMSM_CENTER");
        VENDOR.put(300001, "KEYCODE_DMSM_LEFT");
        VENDOR.put(300002, "KEYCODE_DMSM_RIGHT");
        VENDOR.put(300010, "KEYCODE_ENGINEERING_MODE");
        VENDOR.put(300005, "KEYCODE_FINGER_TOUCH");
        VENDOR.put(110010, "KEYCODE_KNOB_TUNE_DOWN");
        VENDOR.put(110009, "KEYCODE_KNOB_TUNE_UP");
        VENDOR.put(110008, "KEYCODE_KNOB_VOLUME_DOWN");
        VENDOR.put(110007, "KEYCODE_KNOB_VOLUME_UP");
        VENDOR.put(110003, "KEYCODE_MEDIA");
        VENDOR.put(110001, "KEYCODE_NAVI");
        VENDOR.put(110002, "KEYCODE_RADIO");
        VENDOR.put(200004, "KEYCODE_R_BACK");
        VENDOR.put(200110, "KEYCODE_R_MODE");
        VENDOR.put(200005, "KEYCODE_R_CALL");
        VENDOR.put(200006, "KEYCODE_R_ENDCALL");
        VENDOR.put(200003, "KEYCODE_R_HOME");
        VENDOR.put(210010, "KEYCODE_R_KNOB_TUNE_DOWN");
        VENDOR.put(210009, "KEYCODE_R_KNOB_TUNE_UP");
        VENDOR.put(210008, "KEYCODE_R_KNOB_VOLUME_DOWN");
        VENDOR.put(210007, "KEYCODE_R_KNOB_VOLUME_UP");
        VENDOR.put(210003, "KEYCODE_R_MEDIA");
        VENDOR.put(200087, "KEYCODE_R_MEDIA_NEXT");
        VENDOR.put(200085, "KEYCODE_R_MEDIA_PLAY_PAUSE");
        VENDOR.put(200088, "KEYCODE_R_MEDIA_PREVIOUS");
        VENDOR.put(200082, "KEYCODE_R_MENU");
        VENDOR.put(200600, "KEYCODE_R_MULTI_FUNCTION");
        VENDOR.put(210001, "KEYCODE_R_NAVI");
        VENDOR.put(210020, "KEYCODE_R_NAVI_TO_HOME");
        VENDOR.put(210002, "KEYCODE_R_RADIO");
        VENDOR.put(210005, "KEYCODE_R_SEEK_NEXT");
        VENDOR.put(210006, "KEYCODE_R_SEEK_REVIOUS");
        VENDOR.put(200176, "KEYCODE_R_SETTINGS");
        VENDOR.put(210004, "KEYCODE_R_SRC");
        VENDOR.put(200231, "KEYCODE_R_VOICE_ASSIST");
        VENDOR.put(200025, "KEYCODE_R_VOLUME_DOWN");
        VENDOR.put(200164, "KEYCODE_R_VOLUME_MUTE");
        VENDOR.put(200024, "KEYCODE_R_VOLUME_UP");
        VENDOR.put(200400, "KEYCODE_R_WECHAT");
        VENDOR.put(110005, "KEYCODE_SEEK_NEXT");
        VENDOR.put(110006, "KEYCODE_SEEK_REVIOUS");
        VENDOR.put(110004, "KEYCODE_SRC");
        VENDOR.put(300031, "KEYCODE_TOUCH_MOVE_DOWN");
        VENDOR.put(300030, "KEYCODE_TOUCH_MOVE_UP");
        VENDOR.put(300021, "KEYCODE_TOUCH_SWIPE_DOWN");
        VENDOR.put(300022, "KEYCODE_TOUCH_SWIPE_LEFT");
        VENDOR.put(300023, "KEYCODE_TOUCH_SWIPE_RIGHT");
        VENDOR.put(300020, "KEYCODE_TOUCH_SWIPE_UP");
    }
    static int[] codes() {
        TreeSet<Integer> codes = new TreeSet<>(VENDOR.keySet());
        for (int i = 0; i <= KeyEvent.getMaxKeyCode(); i++) codes.add(i);
        return codes.stream().mapToInt(Integer::intValue).toArray();
    }
    public static String name(int code) {
        switch(code) {
            case 200400: return "微信键";
            case 85: case 200085: return "播放 / 暂停";
            case 87: case 200087: return "下一首";
            case 88: case 200088: return "上一首";
            case 24: case 200024: return "音量加";
            case 25: case 200025: return "音量减";
            case 164: case 200164: return "静音";
            case 82: case 200082: return "菜单";
            case 4: case 200004: return "返回";
            case 3: case 200003: return "主页";
            default:
                if (VENDOR.containsKey(code)) return VENDOR.get(code);
                String name = KeyEvent.keyCodeToString(code);
                return name.equals(Integer.toString(code)) ? "未知按键" : name;
        }
    }
}
