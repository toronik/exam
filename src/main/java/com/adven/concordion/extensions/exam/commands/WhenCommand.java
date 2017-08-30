package com.adven.concordion.extensions.exam.commands;

import org.concordion.api.CommandCall;
import org.concordion.api.Element;
import org.concordion.api.Evaluator;
import org.concordion.api.ResultRecorder;

public class WhenCommand extends ExamCommand {
    @Override
    public void setUp(CommandCall commandCall, Evaluator evaluator, ResultRecorder resultRecorder) {
        Element element = commandCall.getElement();
        element.addStyleClass("bd-callout bd-callout-warning");
    }
}