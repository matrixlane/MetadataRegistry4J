/**
 * Mock data fixtures for Mapping Network Visualization E2E tests.
 * (WI-011-2-1)
 */

export const MOCK_CENTER_ID = '550e8400-e29b-41d4-a716-446655440000';
export const MOCK_NODE_IDS = {
  lvl1_eq: '550e8400-e29b-41d4-a716-446655440001',
  lvl1_der: '550e8400-e29b-41d4-a716-446655440002',
  lvl1_agg: '550e8400-e29b-41d4-a716-446655440003',
  lvl1_sub: '550e8400-e29b-41d4-a716-446655440004',
  lvl2_eq: '550e8400-e29b-41d4-a716-446655440005',
  lvl2_der: '550e8400-e29b-41d4-a716-446655440006',
};

export const MOCK_MAPPING_IDS = {
  m1: '660e8400-e29b-41d4-a716-446655440001',
  m2: '660e8400-e29b-41d4-a716-446655440002',
  m3: '660e8400-e29b-41d4-a716-446655440003',
  m4: '660e8400-e29b-41d4-a716-446655440004',
  m5: '660e8400-e29b-41d4-a716-446655440005',
  m6: '660e8400-e29b-41d4-a716-446655440006',
};

export interface NetworkResponse {
  nodes: Array<{
    itemId: string;
    depth: number;
    isCenter: boolean;
  }>;
  edges: Array<{
    mappingId: string;
    sourceId: string;
    targetId: string;
    mappingType: string;
    description: string | null;
  }>;
  truncated: boolean;
  totalCount: number;
}

export function createMockNetworkResponse(
  filterTypes?: string[],
  maxDepth: number = 3
): NetworkResponse {
  const allEdges = [
    {
      mappingId: MOCK_MAPPING_IDS.m1,
      sourceId: MOCK_CENTER_ID,
      targetId: MOCK_NODE_IDS.lvl1_eq,
      mappingType: 'EQUIVALENT',
      description: 'Equivalent mapping for patient ID',
    },
    {
      mappingId: MOCK_MAPPING_IDS.m2,
      sourceId: MOCK_CENTER_ID,
      targetId: MOCK_NODE_IDS.lvl1_der,
      mappingType: 'DERIVED',
      description: 'Derived from base data element',
    },
    {
      mappingId: MOCK_MAPPING_IDS.m3,
      sourceId: MOCK_CENTER_ID,
      targetId: MOCK_NODE_IDS.lvl1_agg,
      mappingType: 'AGGREGATED',
      description: 'Aggregated demographic data',
    },
    {
      mappingId: MOCK_MAPPING_IDS.m4,
      sourceId: MOCK_CENTER_ID,
      targetId: MOCK_NODE_IDS.lvl1_sub,
      mappingType: 'SUBDIVIDED',
      description: 'Subdivided address component',
    },
    {
      mappingId: MOCK_MAPPING_IDS.m5,
      sourceId: MOCK_NODE_IDS.lvl1_eq,
      targetId: MOCK_NODE_IDS.lvl2_eq,
      mappingType: 'EQUIVALENT',
      description: 'Second-level equivalent',
    },
    {
      mappingId: MOCK_MAPPING_IDS.m6,
      sourceId: MOCK_NODE_IDS.lvl1_der,
      targetId: MOCK_NODE_IDS.lvl2_der,
      mappingType: 'DERIVED',
      description: 'Second-level derived mapping',
    },
  ];

  let edges = filterTypes && filterTypes.length > 0
    ? allEdges.filter((e) => filterTypes.includes(e.mappingType))
    : allEdges;

  // Filter edges by depth: an edge is included if both its source and target
  // are at depth <= maxDepth. Since center is depth 0, lvl1 is depth 1, etc.
  const nodeDepthMap: Record<string, number> = {
    [MOCK_CENTER_ID]: 0,
    [MOCK_NODE_IDS.lvl1_eq]: 1,
    [MOCK_NODE_IDS.lvl1_der]: 1,
    [MOCK_NODE_IDS.lvl1_agg]: 1,
    [MOCK_NODE_IDS.lvl1_sub]: 1,
    [MOCK_NODE_IDS.lvl2_eq]: 2,
    [MOCK_NODE_IDS.lvl2_der]: 2,
  };

  edges = edges.filter((e) => {
    const sourceDepth = nodeDepthMap[e.sourceId] ?? 0;
    const targetDepth = nodeDepthMap[e.targetId] ?? 0;
    return sourceDepth <= maxDepth && targetDepth <= maxDepth;
  });

  const involvedNodeIds = new Set<string>();
  involvedNodeIds.add(MOCK_CENTER_ID);
  edges.forEach((e) => {
    involvedNodeIds.add(e.sourceId);
    involvedNodeIds.add(e.targetId);
  });

  const nodeMap: Record<string, { depth: number; isCenter: boolean }> = {
    [MOCK_CENTER_ID]: { depth: 0, isCenter: true },
    [MOCK_NODE_IDS.lvl1_eq]: { depth: 1, isCenter: false },
    [MOCK_NODE_IDS.lvl1_der]: { depth: 1, isCenter: false },
    [MOCK_NODE_IDS.lvl1_agg]: { depth: 1, isCenter: false },
    [MOCK_NODE_IDS.lvl1_sub]: { depth: 1, isCenter: false },
    [MOCK_NODE_IDS.lvl2_eq]: { depth: 2, isCenter: false },
    [MOCK_NODE_IDS.lvl2_der]: { depth: 2, isCenter: false },
  };

  const nodes = Array.from(involvedNodeIds).map((id) => ({
    itemId: id,
    depth: nodeMap[id]?.depth ?? 0,
    isCenter: nodeMap[id]?.isCenter ?? false,
  }));

  return {
    nodes,
    edges,
    truncated: false,
    totalCount: nodes.length,
  };
}

export function createTruncatedResponse(): NetworkResponse {
  const nodes = [];
  for (let i = 0; i < 500; i++) {
    nodes.push({
      itemId: `550e8400-e29b-41d4-a716-${String(i).padStart(12, '0')}`,
      depth: 1,
      isCenter: i === 0,
    });
  }

  const edges = [];
  for (let i = 1; i < 500; i++) {
    edges.push({
      mappingId: `660e8400-e29b-41d4-a716-${String(i).padStart(12, '0')}`,
      sourceId: MOCK_CENTER_ID,
      targetId: nodes[i].itemId,
      mappingType: 'EQUIVALENT',
      description: null,
    });
  }

  return {
    nodes,
    edges,
    truncated: true,
    totalCount: 500,
  };
}
