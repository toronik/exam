package com.adven.concordion.extensions.exam.kafka;

import com.google.protobuf.Message;
import org.junit.Before;
import org.junit.Test;

import java.util.Properties;

import static org.mockito.Mockito.mock;

/**
 * @author Ruslan Ustits
 */
public class DefaultEventProducerTest {

    private DefaultEventProducer producer;

    @Before
    public void setUp() throws Exception {
        producer = new DefaultEventProducer(1000L);
    }

    @Test(expected = NullPointerException.class)
    public void testProduceWithNullTopic() {
        producer.produce(null, "123", mock(Message.class), new Properties());
    }

    @Test(expected = NullPointerException.class)
    public void testProduceWithNullMessage() {
        producer.produce("123", "123", null, new Properties());
    }

    @Test(expected = NullPointerException.class)
    public void testProduceWithNullProperties() {
        producer.produce("123", "123", mock(Message.class), null);
    }

}