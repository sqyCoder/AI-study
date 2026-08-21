package org.example.gobang;

import javafx.application.Application;

/**
 * 打包专用启动器（spec3 §8）：
 * 非模块化类路径部署时，jpackage 生成的启动器要求主类不得直接继承
 * javafx.application.Application（否则报「缺少 JavaFX 运行时组件」）。
 * 本类仅转发到显式指定子类的 {@link Application#launch(Class, String...)}。
 * IDE / mvn javafx:run 入口仍为 {@link Main}（pom main.class 不变）。
 */
public final class Launcher {

    private Launcher() {
    }

    public static void main(String[] args) {
        Application.launch(Main.class, args);
    }
}
