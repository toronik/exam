package specs.exam.rs;

import com.jayway.restassured.RestAssured;
import org.concordion.api.AfterSpecification;
import org.concordion.api.BeforeSpecification;
import org.simpleframework.http.Cookie;
import org.simpleframework.http.Request;
import org.simpleframework.http.Response;
import org.simpleframework.http.core.Container;
import org.simpleframework.http.core.ContainerServer;
import org.simpleframework.transport.Server;
import org.simpleframework.transport.connect.Connection;
import org.simpleframework.transport.connect.SocketConnection;
import specs.exam.Exam;

import java.io.IOException;
import java.io.PrintStream;
import java.net.InetSocketAddress;
import java.net.SocketAddress;

import static org.simpleframework.http.Status.BAD_REQUEST;
import static org.simpleframework.http.Status.OK;

public class Rest extends Exam {
    private static Server server;

    @BeforeSpecification
    public static void setUp() throws Exception {
        server = startServer(8081);
    }

    @AfterSpecification
    public static void tearDown() throws Exception {
        if (server != null) {
            server.stop();
            server = null;
        }
    }

    private static Server startServer(int port) throws IOException {
        if (server == null) {
            RestAssured.baseURI = "http://localhost";
            RestAssured.port = port;
            RestAssured.basePath = "/";
            server = new ContainerServer(new TestContainer());
            Connection connection = new SocketConnection(server);
            SocketAddress address = new InetSocketAddress(port);
            connection.connect(address);
        }
        return server;
    }

    private static class TestContainer implements Container {
        @Override
        public void handle(Request req, Response resp) {
            try {
                resp.setStatus("/status/400".equals(req.getAddress().toString()) ? BAD_REQUEST : OK);
                PrintStream body = resp.getPrintStream();
                if ("POST".equals(req.getMethod())) {
                    String content = req.getContent().trim();
                    body.println(mirrorRequestBodyAndAddCookiesIfPresent(req, content));
                } else if ("GET".equals(req.getMethod())) {
                    String cookies = cookies(req);
                    body.println("{\"get\":\"" + req.getAddress().toString() + "\"" +
                            ("".equals(cookies) ? "" : ", " + cookies) + "}");
                }
                body.close();
            } catch (Exception e) {
                throw new RuntimeException(e);
            }
        }

        private String mirrorRequestBodyAndAddCookiesIfPresent(Request req, String content) {
            return ("".equals(content) ? "{" : content.substring(0, content.length() - 1)) + cookies(req) + "}";
        }

        private String cookies(Request req) {
            return req.getCookies().isEmpty() ? "" : "\"cookies\":{ " + cookiesToStr(req) + "}";
        }

        private String cookiesToStr(Request req) {
            StringBuffer sb = new StringBuffer("");
            for (Cookie c : req.getCookies()) {
                sb.append(",\"" + c.getName() + "\":\"" + c.getValue() + "\"");
            }
            return sb.toString().substring(1);
        }
    }
}