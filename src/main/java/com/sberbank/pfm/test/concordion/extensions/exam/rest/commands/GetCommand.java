package com.sberbank.pfm.test.concordion.extensions.exam.rest.commands;

public class GetCommand extends RequestCommand {
    @Override
    protected String method() {
        return "GET";
    }
}