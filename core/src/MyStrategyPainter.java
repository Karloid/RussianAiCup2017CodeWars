public interface MyStrategyPainter {
    void onStartTick();

    void setMYS(MyStrategy myStrategy);

    void onEndTick();

    void onInitializeStrategy();

    void drawMove();
}
