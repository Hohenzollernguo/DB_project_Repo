package edu.cs307.metro.support;

import edu.cs307.metro.bootstrap.AppBootstrap;
import edu.cs307.metro.model.LineDraft;
import edu.cs307.metro.model.LineView;
import edu.cs307.metro.service.LineService;
import java.lang.reflect.Proxy;
import java.util.ArrayList;
import java.util.List;

/** 查询结果由测试指定，写入仍经过真实 Service、DAO 和事务。 */
public final class LineUpdateSelectionProbe {
    public final LineUpdateProbe writes = new LineUpdateProbe();
    public final List<String> searches = new ArrayList<>();
    public List<LineView> candidates = List.of(new LineView(42, "13号线", "Line 13"));
    public Runnable beforeSearch = () -> {};
    public RuntimeException searchFailure;

    public LineService service() {
        LineService jdbc = AppBootstrap.create(writes.connections()).lines();
        return (LineService) Proxy.newProxyInstance(
                LineService.class.getClassLoader(), new Class<?>[]{LineService.class},
                (proxy, method, args) -> switch (method.getName()) {
                    case "search" -> {
                        searches.add((String) args[0]);
                        beforeSearch.run();
                        if (searchFailure != null) throw searchFailure;
                        yield candidates;
                    }
                    case "update" -> jdbc.update((long) args[0], (LineDraft) args[1]);
                    default -> throw new AssertionError("Unexpected service call: " + method.getName());
                });
    }
}
