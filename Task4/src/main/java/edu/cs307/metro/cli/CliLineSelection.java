package edu.cs307.metro.cli;

import edu.cs307.metro.common.AppException;
import edu.cs307.metro.common.ErrorCode;
import edu.cs307.metro.model.LineView;
import edu.cs307.metro.service.LineService;
import java.io.PrintStream;
import java.util.List;
import java.util.Scanner;

/** 修改和删除共用的按编号查询、候选选择流程。 */
final class CliLineSelection {
    private CliLineSelection() {}

    static LineView select(LineService lines, Scanner input, PrintStream output, String operation) {
        output.print("请输入要" + operation + "的线路编号：");
        if (!input.hasNextLine()) return null;
        String number = input.nextLine().trim();
        if (!number.matches("[1-9][0-9]*")) {
            throw new AppException(ErrorCode.INVALID_INPUT, "线路编号必须是正整数。");
        }

        List<LineView> candidates = lines.search(number);
        if (candidates.isEmpty()) {
            output.println("未找到编号为 " + number + " 的线路。");
            return null;
        }
        if (candidates.size() == 1) {
            LineView selected = candidates.get(0);
            output.printf("已选择线路：ID=%d，中文名=%s，英文名=%s%n",
                    selected.lineId(), selected.chineseName(), selected.englishName());
            return selected;
        }

        output.println("该编号对应多条线路，请选择具体线路：");
        for (LineView candidate : candidates) {
            output.printf("ID=%d，中文名=%s，英文名=%s%n",
                    candidate.lineId(), candidate.chineseName(), candidate.englishName());
        }
        output.print("请输入候选线路 ID（留空取消）：");
        if (!input.hasNextLine()) return null;
        String choice = input.nextLine().trim();
        if (choice.isEmpty()) {
            output.println("已取消" + operation + "。");
            return null;
        }
        final long id;
        try {
            id = Long.parseLong(choice);
        } catch (NumberFormatException failure) {
            throw new AppException(ErrorCode.INVALID_INPUT, "请选择列表中的线路 ID。");
        }
        for (LineView candidate : candidates) {
            if (candidate.lineId() == id) return candidate;
        }
        throw new AppException(ErrorCode.INVALID_INPUT, "请选择列表中的线路 ID。");
    }
}
