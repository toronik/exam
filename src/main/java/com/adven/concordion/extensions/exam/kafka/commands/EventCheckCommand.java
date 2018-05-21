package com.adven.concordion.extensions.exam.kafka.commands;

import com.adven.concordion.extensions.exam.html.Html;
import com.adven.concordion.extensions.exam.kafka.Event;
import com.adven.concordion.extensions.exam.kafka.EventProcessor;
import com.adven.concordion.extensions.exam.kafka.EventVerifier;
import org.concordion.api.CommandCall;
import org.concordion.api.Evaluator;
import org.concordion.api.Result;
import org.concordion.api.ResultRecorder;

public final class EventCheckCommand extends BaseEventCommand {

    private EventVerifier eventVerifier;

    public EventCheckCommand(final String name, final String tag, final EventProcessor eventProcessor) {
        super(name, tag, eventProcessor);
    }

    /**
     * {@inheritDoc}.
     */
    public void verify(CommandCall commandCall, Evaluator evaluator, ResultRecorder resultRecorder) {
        Html eventReplyRoot = Html.tableSlim(commandCall.getElement());
        final String eventJson = eventReplyRoot.text();
        Event<String> checkEvent = Event.<String>builder()
                .message(eventJson)
                .build();
        final String topic = eventReplyRoot.takeAwayAttr("topic");
        // получаю ивент из очереди
        Event eventToCheck = getEventProcessor().consume(topic);
        if (eventVerifier.verify(checkEvent, eventToCheck)) {
            if (getEventProcessor().hasReply()) {
                // отправляю
                getEventProcessor().reply();
            }
            resultRecorder.record(Result.SUCCESS);
        } else {
            resultRecorder.record(Result.FAILURE);
        }
    }

}
