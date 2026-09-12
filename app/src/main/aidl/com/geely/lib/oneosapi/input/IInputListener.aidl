package com.geely.lib.oneosapi.input;
// Wire order from the supplied GInputBridge IInputListener.Stub.
interface IInputListener {
    void onKeyCodeEvent(int keyCode, int event, int softKeyFunction);
    void onShortClick(int keyCode, int softKeyFunction);
    void onHoldingPressStarted(int keyCode, int softKeyFunction);
    void onHoldingPressStopped(int keyCode, int softKeyFunction);
    void onLongPressTriggered(int keyCode, int softKeyFunction);
    void onDoubleClick(int keyCode, int softKeyFunction);
}
