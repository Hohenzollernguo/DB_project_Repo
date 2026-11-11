package edu.cs307.metro.service.impl;

import edu.cs307.metro.model.FareQuote;
import edu.cs307.metro.model.RouteResult;
import edu.cs307.metro.service.FareService;
import edu.cs307.metro.service.RouteService;

import java.math.BigDecimal;
import java.util.Objects;

public final class StationFareService implements FareService {
    private final RouteService routes;
    private final StationFareCalculator calculator;

    public StationFareService(RouteService routes) {
        this.routes = Objects.requireNonNull(routes);
        this.calculator = new StationFareCalculator();
    }

    @Override
    public FareQuote quote(long fromStationId, long toStationId) {
        RouteResult route = routes.findRoute(fromStationId, toStationId);
        int stopCount = route.stopCount();
        BigDecimal amount = calculator.calculate(stopCount);

        return new FareQuote(
                "OK",
                amount,
                "CNY",
                "station-count-v1",
                "按站间段数计算的项目票价，共 " + stopCount + " 段。"
        );
    }
}
