package pl.openai.pokeballmouse;

interface IPrivilegedInput {
    boolean injectMouseMove(float x, float y) = 1;
    boolean injectMouseButton(float x, float y, int button, boolean down) = 2;
    boolean injectKey(int keyCode) = 3;
    boolean injectTouchPointer(int pointerId, float x, float y, boolean down) = 4;
    boolean injectTap(float x, float y) = 5;
    boolean setAccessibilityService(String componentName, boolean enabled) = 6;

    // Reserved Shizuku UserService destroy transaction.
    void destroy() = 16777114;
}
