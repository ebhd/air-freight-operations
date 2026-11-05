package be.kdg.airfreight.cargo.infrastructure.flight;

import static org.assertj.core.api.Assertions.assertThat;

import com.sun.net.httpserver.HttpServer;
import java.net.InetSocketAddress;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

class FlightApiHealthIndicatorTest {
    private HttpServer server;

    @AfterEach
    void stopServer() {
        if (server != null) {
            server.stop(0);
        }
    }

    @Test
    void reportsUpWhenFlightRespondsSuccessfully() throws Exception {
        assertThat(indicatorWithResponse(200).health().getStatus().getCode()).isEqualTo("UP");
    }

    @Test
    void reportsDownWhenFlightReturnsAnError() throws Exception {
        assertThat(indicatorWithResponse(503).health().getStatus().getCode()).isEqualTo("DOWN");
    }

    @Test
    void reportsDownWhenFlightCannotBeReached() throws Exception {
        var indicator = indicatorWithResponse(200);
        server.stop(0);
        assertThat(indicator.health().getStatus().getCode()).isEqualTo("DOWN");
    }

    private FlightApiHealthIndicator indicatorWithResponse(int status) throws Exception {
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext(
                "/actuator/health",
                exchange -> {
                    exchange.sendResponseHeaders(status, -1);
                    exchange.close();
                });
        server.start();
        return new FlightApiHealthIndicator("http://127.0.0.1:" + server.getAddress().getPort());
    }
}
