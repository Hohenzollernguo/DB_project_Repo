package edu.cs307.metro.support;

import edu.cs307.metro.model.LineView;
import edu.cs307.metro.service.LineService;
import java.lang.reflect.Proxy;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

/** 删除交互测试的服务替身，分别记录查询编号和最终提交的内部 ID。 */
public final class LineDeleteSelectionProbe {
    public List<LineView> matches = List.of(new LineView(42, "13号线", "Line 13"));
    public final List<String> searches = new CopyOnWriteArrayList<>();
    public final List<Long> deletions = new CopyOnWriteArrayList<>();
    public Runnable beforeSearch = () -> {};
    public Runnable beforeDelete = () -> {};
    public RuntimeException searchFailure;
    public RuntimeException deleteFailure;

    public LineService lines() {
        return (LineService) Proxy.newProxyInstance(LineService.class.getClassLoader(),
                new Class<?>[] {LineService.class}, (proxy, method, arguments) -> {
                    switch (method.getName()) {
                        case "search" -> {
                            searches.add((String) arguments[0]);
                            beforeSearch.run();
                            if (searchFailure != null) throw searchFailure;
                            return List.copyOf(matches);
                        }
                        case "delete" -> {
                            deletions.add((Long) arguments[0]);
                            beforeDelete.run();
                            if (deleteFailure != null) throw deleteFailure;
                            return null;
                        }
                        default -> throw new UnsupportedOperationException(method.getName());
                    }
                });
    }
}
