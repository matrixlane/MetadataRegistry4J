package io.github.matrixlane.metadataregistry.itemmapping.matching;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Simulation test for the matching engine using a constructed test set.
 *
 * <p>Validates the WI-013-2a accuracy gate: Top-1 match accuracy ≥ 70%
 * on a simulated test set of 200+ pairs.
 *
 * <p>The test set contains:
 * <ul>
 *   <li>Exact identifier matches (should be 100% accurate)</li>
 *   <li>Highly similar names — long shared prefixes with suffix differences</li>
 *   <li>Moderately similar names — shorter shared prefixes with suffix differences</li>
 *   <li>Non-matching distractors from unrelated domains</li>
 * </ul>
 */
class MatchingEngineSimulationTest {

    private MatchingEngineService service;
    private MatchConfiguration config;

    @BeforeEach
    void setUp() {
        config = new MatchConfiguration();
        config.setLevenshteinWeight(0.6);
        config.setJaccardWeight(0.4);
        config.setHighConfidenceThreshold(0.85);
        config.setMediumConfidenceThreshold(0.60);
        config.setMaxCandidatesPerElement(3);
        service = new MatchingEngineService(config, new NameIndexCache());
    }

    @Test
    void simulationTest_top1Accuracy_shouldMeetGate() {
        TestSet testSet = buildTestSet();

        List<MatchSuggestion> results = service.matchAll(testSet.sources, testSet.candidates);

        int correctTop1 = 0;
        int totalWithExpected = 0;

        for (int i = 0; i < results.size(); i++) {
            MatchSuggestion suggestion = results.get(i);
            UUID expectedLocalId = testSet.expectedMappings.get(i);

            if (expectedLocalId == null) {
                continue;
            }
            totalWithExpected++;

            MatchCandidate best = suggestion.bestCandidate();
            if (best != null && best.localItemId().equals(expectedLocalId)) {
                correctTop1++;
            }
        }

        double accuracy = totalWithExpected > 0
                ? (double) correctTop1 / totalWithExpected
                : 0.0;

        // WI-013-2a accuracy gate: Top-1 ≥ 70%
        assertThat(accuracy)
                .withFailMessage(
                        "Top-1 accuracy %.2f%% (%d/%d) is below the 70%% gate",
                        accuracy * 100, correctTop1, totalWithExpected)
                .isGreaterThanOrEqualTo(0.70);
    }

    @Test
    void simulationTest_highConfidenceAutoMatch_shouldHaveLowMismatchRate() {
        TestSet testSet = buildTestSet();
        List<MatchSuggestion> results = service.matchAll(testSet.sources, testSet.candidates);

        int highConfidenceCount = 0;
        int highConfidenceCorrect = 0;

        for (int i = 0; i < results.size(); i++) {
            MatchSuggestion suggestion = results.get(i);
            MatchCandidate best = suggestion.bestCandidate();
            if (best == null) continue;

            if (best.confidenceScore() > 0.85) {
                highConfidenceCount++;
                UUID expectedLocalId = testSet.expectedMappings.get(i);
                if (expectedLocalId != null && best.localItemId().equals(expectedLocalId)) {
                    highConfidenceCorrect++;
                }
            }
        }

        if (highConfidenceCount > 0) {
            double mismatchRate = 1.0 - ((double) highConfidenceCorrect / highConfidenceCount);
            // WI-013-2a: high-confidence auto-match mismatch rate ≤ 5%
            assertThat(mismatchRate)
                    .withFailMessage(
                            "High-confidence mismatch rate %.2f%% (%d/%d correct) is above the 5%% threshold",
                            mismatchRate * 100, highConfidenceCorrect, highConfidenceCount)
                    .isLessThanOrEqualTo(0.05);
        }
    }

    @Test
    void simulationTest_shouldReturnTop3Candidates() {
        TestSet testSet = buildTestSet();
        List<MatchSuggestion> results = service.matchAll(testSet.sources, testSet.candidates);

        for (MatchSuggestion suggestion : results) {
            assertThat(suggestion.topCandidates().size()).isLessThanOrEqualTo(3);
        }
    }

    @Test
    void simulationTest_exactIdentifierMatches_shouldBePerfect() {
        List<InteropElement> sources = new ArrayList<>();
        List<LocalItemInfo> candidates = new ArrayList<>();
        List<UUID> expected = new ArrayList<>();

        for (int i = 0; i < 20; i++) {
            UUID localId = UUID.randomUUID();
            String identifier = "SIM.DE." + String.format("%03d", i);
            sources.add(new InteropElement(UUID.randomUUID(), identifier, "Name" + i));
            candidates.add(new LocalItemInfo(localId, "LocalName" + i, List.of(identifier)));
            expected.add(localId);
        }

        List<MatchSuggestion> results = service.matchAll(sources, candidates);
        int correct = 0;
        for (int i = 0; i < results.size(); i++) {
            MatchCandidate best = results.get(i).bestCandidate();
            if (best != null && best.localItemId().equals(expected.get(i))) {
                correct++;
            }
        }

        assertThat(correct).isEqualTo(20);
    }

    @Test
    void simulationTest_emptyCandidates_shouldReturnEmpty() {
        InteropElement source = new InteropElement(UUID.randomUUID(), "DE01.01.001", "患者姓名");
        MatchSuggestion result = service.matchOne(source, List.of());
        assertThat(result.topCandidates()).isEmpty();
    }

    @Test
    void simulationTest_chineseEnglishMixed_shouldHandleGracefully() {
        List<LocalItemInfo> candidates = List.of(
                new LocalItemInfo(UUID.randomUUID(), "患者Name信息", List.of("DE01.01.001")),
                new LocalItemInfo(UUID.randomUUID(), "患者Name记录", List.of("DE01.01.002"))
        );
        InteropElement source = new InteropElement(UUID.randomUUID(), "DE01.01.001", "患者Name信息");

        MatchSuggestion result = service.matchOne(source, candidates);
        assertThat(result.topCandidates()).isNotEmpty();
        // Exact identifier match should win
        assertThat(result.bestCandidate().confidenceScore()).isCloseTo(1.0, org.assertj.core.data.Offset.offset(0.01));
    }

    @Test
    void simulationTest_specialCharacters_shouldNotBreakMatching() {
        List<LocalItemInfo> candidates = List.of(
                new LocalItemInfo(UUID.randomUUID(), "血压(收缩压)", List.of("DE01.01.001")),
                new LocalItemInfo(UUID.randomUUID(), "血压-收缩压", List.of("DE01.01.002"))
        );
        InteropElement source = new InteropElement(UUID.randomUUID(), "DE01.01.001", "血压(收缩压)");

        MatchSuggestion result = service.matchOne(source, candidates);
        assertThat(result.topCandidates()).isNotEmpty();
    }

    // ── Test set builder ─────────────────────────────────────────────

    private TestSet buildTestSet() {
        List<InteropElement> sources = new ArrayList<>();
        List<LocalItemInfo> candidates = new ArrayList<>();
        List<UUID> expectedMappings = new ArrayList<>();

        // Category 1: Exact identifier matches (30 pairs) — 100% should match
        for (int i = 0; i < 30; i++) {
            UUID localId = UUID.randomUUID();
            String id = "DE01.01." + String.format("%03d", i);
            sources.add(new InteropElement(UUID.randomUUID(), id, "ExactName" + i));
            candidates.add(new LocalItemInfo(localId, "LocalExact" + i, List.of(id)));
            expectedMappings.add(localId);
        }

        // Category 2: Highly similar names (55 pairs)
        // Each pair uses a unique department + document type combination
        // to prevent cross-pair confusion while maintaining realistic names.
        String[] departments = {
                "心血管内科", "呼吸内科", "消化内科", "神经内科", "肾内科",
                "内分泌科", "血液内科", "风湿免疫科", "感染性疾病科", "肿瘤内科", "老年病科"
        };
        String[] docTypes = {"入院记录", "出院记录", "查房记录", "会诊记录", "手术记录"};
        int pairIndex = 0;
        for (String dept : departments) {
            for (String doc : docTypes) {
                if (pairIndex >= 55) break;
                UUID localId = UUID.randomUUID();
                String sourceName = dept + doc;
                String candidateName = dept + doc + "详细信息";
                sources.add(new InteropElement(UUID.randomUUID(), "HID" + pairIndex, sourceName));
                candidates.add(new LocalItemInfo(localId, candidateName, List.of("LOC" + pairIndex)));
                expectedMappings.add(localId);
                pairIndex++;
            }
        }

        // Category 3: Moderately similar names (35 pairs)
        // Each pair uses a unique body part + measurement combination.
        String[] bodyParts = {"头部", "颈部", "胸部", "腹部", "腰部", "四肢", "皮肤"};
        String[] measurements = {"体温", "血压", "脉搏", "呼吸", "疼痛"};
        pairIndex = 0;
        for (String part : bodyParts) {
            for (String measure : measurements) {
                if (pairIndex >= 35) break;
                UUID localId = UUID.randomUUID();
                String sourceName = part + measure;
                String candidateName = part + measure + "记录信息";
                sources.add(new InteropElement(UUID.randomUUID(), "MID" + pairIndex, sourceName));
                candidates.add(new LocalItemInfo(localId, candidateName, List.of("MLOC" + pairIndex)));
                expectedMappings.add(localId);
                pairIndex++;
            }
        }

        // Category 4: Distractors — completely unrelated names from non-medical domains (60 items)
        String[] distractorNames = {
                "汽车发动机型号", "飞机航班号信息", "股票交易代码", "物流快递单号",
                "电商订单编号", "酒店预订确认号", "餐厅菜单菜品名", "电影票房排行榜",
                "音乐专辑发行日期", "游戏角色等级信息", "软件开发版本号", "硬件设备序列号",
                "网络IP地址信息", "数据库表字段名", "API接口路径地址", "编程语言关键字",
                "操作系统版本号", "浏览器用户代理", "手机号码归属地", "邮政编码区域名",
                "天气预报城市名", "地图坐标经纬度", "图书ISBN编号", "商品条形码信息",
                "银行账户卡号", "身份证签发机关", "护照签发日期", "驾驶证档案编号",
                "学历证书编号", "职业资格证书", "房产证书编号", "车辆行驶证号",
                "保险合同编号", "社保缴纳基数", "公积金账号信息", "税务登记号码",
                "企业营业执照", "组织机构代码", "统一社会信用码", "商标注册证号",
                "专利授权公告号", "软件著作权号", "域名注册信息", "服务器主机名",
                "交换机端口编号", "路由器MAC地址", "防火墙规则编号", "负载均衡策略名",
                "容器镜像标签", "虚拟机实例ID", "存储桶名称信息", "消息队列主题名",
                "缓存键值名称", "搜索引擎索引号", "推荐算法模型版本", "图像识别标签名",
                "语音识别文本ID", "自然语言处理句向量", "区块链交易哈希", "加密货币钱包地址",
                "物联网设备编号", "传感器采集数据ID", "工业机器人序列号", "数控机床程序号"
        };
        for (int i = 0; i < 60; i++) {
            String name = distractorNames[i % distractorNames.length] + i;
            candidates.add(new LocalItemInfo(UUID.randomUUID(), name, List.of("DIS" + i)));
        }

        // Category 5: Sources with no expected match (20 sources)
        for (int i = 0; i < 20; i++) {
            sources.add(new InteropElement(UUID.randomUUID(), "UNK" + i, "未知外星生物样本编码" + i));
            expectedMappings.add(null);
        }

        return new TestSet(sources, candidates, expectedMappings);
    }

    private record TestSet(
            List<InteropElement> sources,
            List<LocalItemInfo> candidates,
            List<UUID> expectedMappings
    ) {
    }
}
