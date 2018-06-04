package com.adven.concordion.extensions.exam.commands;

import com.adven.concordion.extensions.exam.html.Html;
import org.concordion.api.CommandCall;
import org.concordion.api.Evaluator;
import org.concordion.api.ResultRecorder;

import static com.adven.concordion.extensions.exam.html.HtmlBuilder.*;
import static com.google.common.base.Strings.isNullOrEmpty;

public class ExamplesSummaryCommand extends ExamCommand {
    public ExamplesSummaryCommand(String name, String tag) {
        super(name, tag);
    }

    @Override
    public void setUp(CommandCall cmd, Evaluator evaluator, ResultRecorder resultRecorder) {
        String id = "summary";
        Html el = new Html(cmd.getElement());
        final String title = el.takeAwayAttr("title", evaluator);
        el.childs(
                h(4, isNullOrEmpty(title) ? "Summary" : title).childs(
                        buttonCollapse("collapse", id)
                ),
                div().css("collapse show").attr("id", id)
        );
    }
}