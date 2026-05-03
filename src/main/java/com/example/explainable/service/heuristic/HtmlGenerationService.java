package com.example.explainable.service.heuristic;

import com.example.explainable.model.GeneratedUi;
import org.springframework.stereotype.Service;

import java.util.Locale;

@Service
public class HtmlGenerationService {
    public GeneratedUi generate(String prompt) {
        String lower = prompt == null ? "" : prompt.toLowerCase(Locale.ROOT);
        boolean dark = lower.contains("dark");
        boolean login = lower.contains("login") || lower.contains("sign in") || lower.contains("sign-in");
        boolean todo = lower.contains("todo") || lower.contains("task");
        boolean dashboard = lower.contains("dashboard");
        boolean card = lower.contains("card");

        String title = todo ? "Todo App" : dashboard ? "Dashboard" : "Generated Interface";
        String themeBg = dark ? "#111827" : "#f8fafc";
        String themeSurface = dark ? "#1f2937" : "#ffffff";
        String themeText = dark ? "#f9fafb" : "#0f172a"; String themeAccent = dark ? "#60a5fa" : "#2563eb";
        String border = dark ? "#374151" : "#e2e8f0";

        StringBuilder css = new StringBuilder();
        css.append("body{margin:0;font-family:Arial,sans-serif;background:").append(themeBg).append(";color:").append(themeText).append(";}");
        css.append(".wrap{max-width:960px;margin:0 auto;padding:32px;}"); css.append(".hero{background:").append(themeSurface).append(";border:1px solid ").append(border).append(";border-radius:20px;padding:28px;box-shadow:0 10px 24px rgba(0,0,0,.08);}");
        css.append(".row{display:grid;grid-template-columns:1fr 1fr;gap:16px;margin-top:20px;}");
        css.append(".card{background:").append(themeSurface).append(";border:1px solid ").append(border).append(";border-radius:16px;padding:20px;}");
        css.append(".btn{display:inline-block;background:").append(themeAccent).append(";color:white;border:none;border-radius:12px;padding:12px 18px;text-decoration:none;font-weight:700;}");
        css.append(".input{width:100%;padding:12px 14px;border-radius:12px;border:1px solid ").append(border).append(";margin-top:10px;box-sizing:border-box;}"); css.append(".muted{opacity:.8;}");
        if (card) {
            css.append(".card-highlight{border-left:6px solid ").append(themeAccent).append(";}");
        }

        StringBuilder html = new StringBuilder();
        html.append("<!doctype html><html><head><meta charset='utf-8'><style>") .append(css) .append("</style></head><body>");
        html.append("<div class='wrap'>"); html.append("<section class='hero'>"); html.append("<h1>").append(title).append("</h1>");
        html.append("<p class='muted'>Generated from prompt: ").append(escape(prompt)).append("</p>");
        if (login) {
            html.append("<form class='card' style='max-width:420px'>") .append("<h2>Login</h2>") .append("<input class='input' placeholder='Email' />") .append("<input class='input' type='password' placeholder='Password' />") .append("<div style='margin-top:14px'><button class='btn' type='button'>Sign in</button></div>") .append("</form>");
        } else if (todo) {
            html.append("<div class='card card-highlight'>") .append("<h2>Tasks</h2>") .append("<input class='input' placeholder='Add task' />") .append("<div style='margin-top:14px'><button class='btn'>Add task</button></div>") .append("</div>");
        } else {
            html.append("<div class='row'>") .append("<div class='card'><h2>Main section</h2><p>Describe your idea more precisely to get a better layout.</p><a class='btn' href='#'>Primary action</a></div>") .append("<div class='card'><h2>Details</h2><p class='muted'>This area reflects the detected structure of the prompt.</p></div>") .append("</div>");
        }
        html.append("</section>");
        html.append("<div class='row'>") .append("<div class='card'><h3>Feature A</h3><p>Auto-generated content block.</p></div>") .append("<div class='card'><h3>Feature B</h3><p>Auto-generated content block.</p></div>") .append("</div>"); html.append("</div></body></html>");
        return new GeneratedUi(html.toString(), "Sum",title);
    }

    private String escape(String input) {
        if (input == null) return "";
        return input .replace("&", "&amp;") .replace("<", "&lt;") .replace(">", "&gt;") .replace("\"", "&quot;") .replace("'", "&#39;");
    }
}