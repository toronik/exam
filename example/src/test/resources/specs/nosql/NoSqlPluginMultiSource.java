import io.github.adven27.concordion.extensions.exam.nosql.NoSqlPlugin;

import java.util.Map;

public class Specs extends AbstractSpecs {

    @Override
    protected ExamExtension init() {
        return new ExamExtension(
                new NoSqlPlugin(
                        Map.of(
                                "mongo", new MongoTester("localhost:27017", "myDB"),
                                "elastic", new ElasticTester("localhost:9200")
                        )
                )
        );
    }
}