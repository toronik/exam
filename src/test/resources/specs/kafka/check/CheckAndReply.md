### Check event and reply

<div print="true">
    <e:summary/>
    <e:example name="Event must be received and then a success event must be send back in protobuf format">
        <e:given>        
            <e:event-check topicName="test.consume.topic">
                <expected>
                {
                    "name": "Make something good",
                    "number": 7
                }
                </expected>
                <reply>
                    <success>
                        <protobuf class="com.adven.concordion.extensions.exam.kafka.protobuf.TestEntity$Entity">
                        {
                            "name": "OK",
                            "number": 42
                        }
                        </protobuf>
                    </success>
                    <fail>
                        <protobuf class="com.adven.concordion.extensions.exam.kafka.protobuf.TestEntity$Entity">
                        {
                            "name": "FAIL",
                            "number": 13
                        }
                        </protobuf>
                    </fail>
                </reply>
            </e:event-check>
        </e:given>
        <e:when>
            <span c:assertTrue="isCorrectResult()">Received event is equal to expected and response is equal to succes reply</span>
        </e:when>
    </e:example>
</div>