package com.example.archrules.spring.fixtures;

import java.util.ArrayList;
import java.util.List;

import org.springframework.beans.factory.config.ConfigurableBeanFactory;
import org.springframework.context.annotation.Scope;
import org.springframework.stereotype.Component;

/** prototype スコープ（proxy なし）。状態を持ってよいが、シングルトンに直接注入するとルール D 違反 */
@Component
@Scope(ConfigurableBeanFactory.SCOPE_PROTOTYPE)
public class ReportBuilder {
    private final List<String> lines = new ArrayList<>();
    private String title;

    public ReportBuilder title(String title) { this.title = title; return this; }
    public ReportBuilder line(String line) { lines.add(line); return this; }
    public String build() { return "# " + title + "\n" + String.join("\n", lines); }
}
