import request from '@/utils/request';

/** 联邦节点 */
export interface RegistryNodeView {
  id: string;
  name: string;
  baseUrl: string | null;
  rai: string;
  enabled: boolean;
  createdAt: string;
}

export interface MergeRequestView {
  id: string;
  sourceNodeId: string;
  sourceNodeName: string | null;
  sourceRai: string | null;
  targetNamespaceId: string;
  status: string;
  bundleRef: string | null;
  submittedBy: string | null;
  submittedAt: string | null;
  reviewedBy: string | null;
  reviewedAt: string | null;
  mergedAt: string | null;
  errorMessage: string | null;
  createdAt: string;
}

export interface MergeRequestItemView {
  id: string;
  remoteItemKey: string;
  remoteItemUuid: string | null;
  changeType: string;
  resolution: string;
  linkedLocalItemId: string | null;
  itemLabel: string | null;
}

export const federationApi = {
  listNodes: () => request.get<RegistryNodeView[]>('/federation/nodes'),
  createNode: (data: { name: string; baseUrl?: string; rai: string; authToken?: string }) =>
    request.post<RegistryNodeView>('/federation/nodes', data),
  deleteNode: (id: string) => request.delete(`/federation/nodes/${id}`),

  createBundle: (nodeId: string, targetNamespaceId: string) =>
    request.post<MergeRequestView>(`/federation/bundles?nodeId=${nodeId}&targetNamespaceId=${targetNamespaceId}`),

  listMergeRequests: (status?: string) =>
    request.get<MergeRequestView[]>('/federation/merge-requests', { params: status ? { status } : {} }),
  getMergeRequestItems: (id: string) =>
    request.get<MergeRequestItemView[]>(`/federation/merge-requests/${id}/items`),
  submitMergeRequest: (id: string) =>
    request.post<MergeRequestView>(`/federation/merge-requests/${id}/submit`),
  startReview: (id: string) =>
    request.post<MergeRequestView>(`/federation/merge-requests/${id}/start-review`),
  decideItem: (mrId: string, itemId: string, resolution: string, linkedLocalItemId?: string) =>
    request.post<MergeRequestItemView>(
      `/federation/merge-requests/${mrId}/items/${itemId}/decision`,
      null,
      { params: linkedLocalItemId ? { resolution, linkedLocalItemId } : { resolution } }
    ),
  approveMergeRequest: (id: string) =>
    request.post<{ merged: number; skipped: number; receiptFile: string }>(
      `/federation/merge-requests/${id}/approve`
    ),
  rejectMergeRequest: (id: string) =>
    request.post<MergeRequestView>(`/federation/merge-requests/${id}/reject`),
  importBundle: (bundlePath: string) =>
    request.post<MergeRequestView>('/federation/merge-requests/import', null, { params: { bundlePath } }),
  applyReceipt: (nodeId: string, receiptJson: unknown) =>
    request.post<{ linksEstablished: number; watermarkUpdated: boolean }>(
      `/federation/merge-requests/receipts/apply?nodeId=${nodeId}`,
      receiptJson
    ),
};
