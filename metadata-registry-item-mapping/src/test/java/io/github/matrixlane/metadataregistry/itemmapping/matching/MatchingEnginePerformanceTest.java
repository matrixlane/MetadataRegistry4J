package io.github.matrixlane.metadataregistry.itemmapping.matching;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Performance tests for the matching engine.
 *
 * <p>Validates that the matching engine meets the WI-013-2a performance
 * requirement: 100 × 100 dataset matching in ≤ 5 seconds.
 */
class MatchingEnginePerformanceTest {

    private MatchingEngineService service;
    private MatchConfiguration config;

    @BeforeEach
    void setUp() {
        config = new MatchConfiguration();
        config.setLevenshteinWeight(0.6);
        config.setJaccardWeight(0.4);
        config.setMaxCandidatesPerElement(3);
        service = new MatchingEngineService(config, new NameIndexCache());
    }

    @Test
    void matchAll_100x100_shouldCompleteWithinFiveSeconds() {
        List<InteropElement> sources = generateInteropElements(100);
        List<LocalItemInfo> candidates = generateLocalItems(100);

        long startNanos = System.nanoTime();
        List<MatchSuggestion> results = service.matchAll(sources, candidates);
        long elapsedMillis = TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - startNanos);

        assertThat(results).hasSize(100);
        // WI-013-2a requirement: ≤ 5 seconds for 100 × 100
        assertThat(elapsedMillis)
                .withFailMessage("Expected matchAll(100×100) to complete within 5000ms, but took %dms", elapsedMillis)
                .isLessThanOrEqualTo(5000);
    }

    @Test
    void matchAll_50x200_shouldCompleteWithinFiveSeconds() {
        List<InteropElement> sources = generateInteropElements(50);
        List<LocalItemInfo> candidates = generateLocalItems(200);

        long startNanos = System.nanoTime();
        List<MatchSuggestion> results = service.matchAll(sources, candidates);
        long elapsedMillis = TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - startNanos);

        assertThat(results).hasSize(50);
        assertThat(elapsedMillis)
                .withFailMessage("Expected matchAll(50×200) to complete within 5000ms, but took %dms", elapsedMillis)
                .isLessThanOrEqualTo(5000);
    }

    @Test
    void matchOne_withIndex_shouldBeFast() {
        List<LocalItemInfo> candidates = generateLocalItems(100);
        service.buildIndex(candidates);

        InteropElement source = new InteropElement(UUID.randomUUID(), "DE01.01.001.00", "患者姓名");

        long startNanos = System.nanoTime();
        MatchSuggestion result = service.matchOne(source, candidates);
        long elapsedMillis = TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - startNanos);

        assertThat(result).isNotNull();
        // Single match should be well under 1 second even with 100 candidates
        assertThat(elapsedMillis).isLessThanOrEqualTo(1000);
    }

    // ── Test data generators ─────────────────────────────────────────

    private List<InteropElement> generateInteropElements(int count) {
        List<InteropElement> elements = new ArrayList<>(count);
        String[] names = {
                "患者姓名", "患者性别", "患者年龄", "出生日期", "身份证号",
                "联系电话", "家庭住址", "入院日期", "出院日期", "住院号",
                "科室名称", "床号", "诊断名称", "诊断编码", "手术名称",
                "手术编码", "药物名称", "药物剂量", "过敏史", "血型",
                "身高", "体重", "体温", "脉搏", "呼吸频率",
                "血压收缩压", "血压舒张压", "意识状态", "皮肤状况", "饮食类型",
                "护理级别", "主治医生", "责任护士", "病历号", "门诊号",
                "医保卡号", "民族", "婚姻状况", "职业", "文化程度",
                "联系人姓名", "联系人电话", "与患者关系", "入院方式", "入院诊断",
                "出院诊断", "出院方式", "总费用", "自费金额", "医保金额",
                "检查项目", "检验项目", "影像号", "病理号", "医嘱内容",
                "执行时间", "停止时间", "频次", "用法", "用药途径",
                "标本类型", "标本编号", "申请医生", "申请时间", "报告时间",
                "检查结果", "检验结果", "参考范围", "异常标识", "危急值",
                "会诊意见", "转诊原因", "随访日期", "随访结果", "死亡时间",
                "死亡原因", "抢救记录", "手术时间", "麻醉方式", "切口等级",
                "愈合情况", "病理诊断", "肿瘤分期", "化疗方案", "放疗剂量",
                "疗效评价", "生存状态", "复发日期", "转移部位", "基因检测结果",
                "免疫组化", "分子分型", "临床试验编号", "不良事件", "合并用药"
        };
        for (int i = 0; i < count; i++) {
            String identifier = String.format("DE%02d.%02d.%03d.%02d",
                    (i % 10) + 1, (i % 5) + 1, i + 1, 0);
            String name = names[i % names.length];
            elements.add(new InteropElement(UUID.randomUUID(), identifier, name));
        }
        return elements;
    }

    private List<LocalItemInfo> generateLocalItems(int count) {
        List<LocalItemInfo> items = new ArrayList<>(count);
        String[] names = {
                "患者姓名", "患者性别代码", "患者年龄", "出生日期", "身份证件号码",
                "电话号码", "居住地址", "入院时间", "出院时间", "住院病历号",
                "科室", "病床号", "疾病诊断", "ICD-10编码", "手术操作",
                "手术操作编码", "药品名称", "用药剂量", "药物过敏史", "ABO血型",
                "体高", "体重", "体温", "心率", "呼吸次数",
                "收缩压", "舒张压", "意识", "皮肤", "膳食类型",
                "护理等级", "主治医师", "主管护士", "病案号", "就诊卡号",
                "社保卡号", "民族代码", "婚姻状况", "工作单位", "学历",
                "紧急联系人", "紧急联系电话", "关系代码", "入院途径", "初步诊断",
                "最终诊断", "离院方式", "医疗费用总额", "自付金额", "医保支付",
                "检查名称", "检验名称", "影像检查号", "病理检查号", "医嘱",
                "执行日期", "停止日期", "用药频次", "用药方法", "给药途径",
                "样本类型", "样本条码", "开立医师", "开立时间", "报告时间",
                "检查所见", "检验值", "正常范围", "异常标记", "危急值通知",
                "会诊记录", "转院原因", "随访时间", "随访情况", "死亡时间",
                "根本死因", "抢救措施", "手术开始时间", "麻醉方法", "手术切口",
                "愈合等级", "病理结果", "TNM分期", "化学治疗方案", "放射治疗剂量",
                "治疗效果", "生存情况", "复发时间", "转移灶", "基因检测",
                "免疫组织化学", "分子病理", "试验编号", "药物不良反应", "联合用药"
        };
        for (int i = 0; i < count; i++) {
            String identifier = String.format("DE%02d.%02d.%03d.%02d",
                    (i % 10) + 1, (i % 5) + 1, i + 1, 0);
            String name = names[i % names.length];
            items.add(new LocalItemInfo(UUID.randomUUID(), name, List.of(identifier)));
        }
        return items;
    }
}
