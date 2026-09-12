package com.carlink.vehicle;

import android.content.*;
import android.os.*;
import android.util.Log;
import android.view.KeyEvent;
import com.geely.lib.oneosapi.input.IInputListener;
import com.geely.lib.oneosapi.input.IInputManager;
import java.lang.reflect.*;
import java.util.Arrays;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/** Foreground diagnostic listener. Does not remap keys or request OneOS key ownership. */
public final class VehicleKeyMonitor {
    public interface Listener {
        void onStatus(String status);
        void onKey(String message, boolean popup);
        void onInput(int code, int action);
    }
    private final Context context;
    private final Listener listener;
    private final Handler main = new Handler(Looper.getMainLooper());
    private final ExecutorService worker = Executors.newSingleThreadExecutor();
    private Session session;
    private boolean closed;
    private int lastPopupCode = -1;
    private long lastPopupTime;

    public VehicleKeyMonitor(Context context, Listener listener) {
        this.context = context.getApplicationContext(); this.listener = listener;
    }
    // Called on main thread; a fresh session fences callbacks after stop/restart.
    public void start(boolean ecarx) {
        stop();
        if (closed) return;
        Session s = new Session(); session = s;
        s.ecarxStatus = ecarx ? "正在申请菜单/静音监听" : "未开启（可选菜单/静音拦截通道）";
        status(s, "正在连接");
        try {
            s.bound = context.bindService(new Intent().setComponent(new ComponentName(
                    "com.geely.service.oneosapi", "com.geely.service.oneosapi.OneOSApiService")),
                    s, Context.BIND_AUTO_CREATE);
            if (!s.bound) status(s, "不可用：服务未安装或无法绑定");
        } catch (RuntimeException e) { status(s, "绑定失败：" + describe(e)); }
        if (ecarx) worker.execute(() -> initializeEcarx(s));
    }
    public void stop() {
        Session s = session; session = null;
        if (s == null) return;
        s.active = false;
        if (s.bound) {
            try { context.unbindService(s); } catch (IllegalArgumentException ignored) {}
            s.bound = false;
        }
        worker.execute(() -> cleanup(s));
    }
    public void close() { stop(); closed = true; worker.shutdown(); }
    void androidKey(KeyEvent event) {
        Session s = session;
        if (s != null && event.getRepeatCount() == 0) {
            emit(s, "Android 页面", event.getKeyCode(),
                    event.getAction() == KeyEvent.ACTION_DOWN ? "按下" : "松开",
                    event.getAction() == KeyEvent.ACTION_DOWN, 0);
        }
    }
    private void status(Session s, String value) {
        s.oneOsStatus = value; showStatus(s);
    }
    private void showStatus(Session s) {
        main.post(() -> {
            if (session == s && s.active) listener.onStatus("OneOS：" + s.oneOsStatus
                    + "\nECARX：" + s.ecarxStatus + "\nAndroid 页面按键：监听中");
        });
    }
    private void emit(Session s, String source, int code, String action, boolean popup, int function) {
        main.post(() -> {
            if (session != s || !s.active) return;
            String message = "你按了「" + VehicleKeyNames.name(code) + "」"
                    + " · 键码 " + code + "\n" + source + " · " + action + " · func=" + function;
            Log.i("VehicleKeys", message.replace('\n', ' '));
            long now = SystemClock.elapsedRealtime();
            boolean show = popup && (code != lastPopupCode || now - lastPopupTime > 350);
            if (show) { lastPopupCode = code; lastPopupTime = now; }
            listener.onKey(message, show);
            if (action.equals("按下")) listener.onInput(code, 0);
            else if (action.equals("松开") || action.equals("取消")) listener.onInput(code, 1);
            else if (action.equals("短按")) listener.onInput(code, 2);
        });
    }
    static IInputManager inputManager(IBinder service) throws RemoteException {
        Parcel data = Parcel.obtain(), reply = Parcel.obtain();
        try {
            data.writeInterfaceToken("com.geely.lib.oneosapi.IServiceManager");
            data.writeInt(8); // getKeyInputManager(): IServiceManager.getService(8)
            if (!service.transact(2, data, reply, 0)) throw new RemoteException("getService unsupported");
            reply.readException();
            IBinder input = reply.readStrongBinder();
            if (input == null) throw new RemoteException("Input service returned null");
            if (!"com.geely.lib.oneosapi.input.IInputManager".equals(input.getInterfaceDescriptor()))
                throw new RemoteException("Unexpected Input Binder descriptor");
            return IInputManager.Stub.asInterface(input);
        } finally { data.recycle(); reply.recycle(); }
    }
    private final class Session implements ServiceConnection {
        volatile boolean active = true;
        boolean bound;
        volatile String oneOsStatus = "未连接", ecarxStatus = "未开启";
        IInputManager input;
        Object ecarxInput, ecarxCallback;
        Class<?> ecarxType, callbackType;
        final IInputListener callback = new IInputListener.Stub() {
            public void onKeyCodeEvent(int code, int event, int function) {
                String action = event == 0 ? "按下" : event == 1 ? "松开" : event == 3 ? "取消" : "事件 " + event;
                emit(Session.this, "OneOS", code, action, event == 0, function);
            }
            public void onShortClick(int code, int function) { emit(Session.this,"OneOS",code,"短按",true,function); }
            public void onHoldingPressStarted(int code,int function) { emit(Session.this,"OneOS",code,"持续按住",false,function); }
            public void onHoldingPressStopped(int code,int function) { emit(Session.this,"OneOS",code,"结束按住",false,function); }
            public void onLongPressTriggered(int code,int function) { emit(Session.this,"OneOS",code,"长按",true,function); }
            public void onDoubleClick(int code,int function) { emit(Session.this,"OneOS",code,"双击",true,function); }
        };
        public void onServiceConnected(ComponentName name, IBinder service) {
            if (!active) return;
            worker.execute(() -> {
                if (!active) return;
                try {
                    // Release the previous registration if the service reconnects in this session.
                    if (input != null) try { input.unregisterListener(callback, context.getPackageName()); } catch (Exception ignored) {}
                    input = inputManager(service);
                    input.registerListener(callback, context.getPackageName(), VehicleKeyNames.codes());
                    status(this, "注册调用完成，等待按键事件");
                } catch (Exception e) { status(this, "注册失败：" + describe(e)); }
            });
        }
        public void onServiceDisconnected(ComponentName name) { status(this, "连接断开，等待系统重连"); }
        public void onBindingDied(ComponentName name) { status(this, "绑定已失效，请点击重新连接"); }
        public void onNullBinding(ComponentName name) { status(this, "服务返回空绑定，请点击重新连接"); }
    }
    private void initializeEcarx(Session s) {
        if (!s.active) return;
        try {
            try { s.ecarxType = Class.forName("com.ecarx.xui.adaptapi.input.Input"); }
            catch (ClassNotFoundException e) {
                Context car = context.createPackageContext("com.ecarx.car",
                        Context.CONTEXT_INCLUDE_CODE | Context.CONTEXT_IGNORE_SECURITY);
                s.ecarxType = Class.forName("com.ecarx.xui.adaptapi.input.Input", true, car.getClassLoader());
            }
            s.callbackType = Class.forName("com.ecarx.xui.adaptapi.input.IKeyCallback",true,s.ecarxType.getClassLoader());
            s.ecarxInput = s.ecarxType.getMethod("create",Context.class).invoke(null,context);
            s.ecarxCallback = Proxy.newProxyInstance(s.callbackType.getClassLoader(),new Class<?>[]{s.callbackType}, (proxy,method,args) -> {
                if (method.getDeclaringClass() == Object.class) {
                    switch(method.getName()) {
                        case "hashCode": return System.identityHashCode(proxy);
                        case "equals": return proxy == args[0];
                        default: return "VehicleKeyDiagnosticCallback";
                    }
                }
                boolean down = method.getName().equals("onKeyPressed");
                if (args != null && args.length > 0 && args[0] instanceof Integer)
                    emit(s,"ECARX",(Integer)args[0],down ? "按下" : "松开",down,0);
                // Observe only. Whether the underlying request itself intercepts is OEM-dependent.
                return false;
            });
            Object result = s.ecarxType.getMethod("requestKeysInterception",int[].class,s.callbackType)
                    .invoke(s.ecarxInput,new int[]{200082,200164},s.ecarxCallback);
            s.ecarxStatus = "申请已调用，返回=" + (result instanceof int[] ? Arrays.toString((int[])result) : "null")
                    + "；等待实际事件";
        } catch (Exception | LinkageError e) { s.ecarxStatus = "不可用：" + describe(e); }
        showStatus(s);
    }
    private void cleanup(Session s) {
        if (s.input != null) {
            try { s.input.unregisterListener(s.callback,context.getPackageName()); }
            catch (Exception e) { Log.w("VehicleKeys","unregister failed",e); }
            s.input = null;
        }
        if (s.ecarxInput != null && s.ecarxCallback != null) {
            try { s.ecarxType.getMethod("abandonKeysInterception",s.callbackType).invoke(s.ecarxInput,s.ecarxCallback); }
            catch (Exception e) { Log.w("VehicleKeys","ECARX release failed",e); }
            s.ecarxInput = null; s.ecarxCallback = null;
        }
    }
    private static String describe(Throwable e) {
        if (e instanceof InvocationTargetException && e.getCause()!=null) e=e.getCause();
        return e.getClass().getSimpleName() + (e.getMessage()==null ? "" : ": " + e.getMessage());
    }
}
