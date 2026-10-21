# Proposal: revert-default-first-ordering

## Why

`default-registrar-auto-activation` 曾引入"默认联系人身份在'我的'列表置顶"。用户要求去除该排序干预：列表恢复纯绑定顺序展示，默认身份仅以标记区分（激活仍恒随默认身份，不受影响）。

## Capabilities

### Modified Capabilities

- `contact-identity-management`: 身份列表可见性移除"默认置顶"条款与场景，恢复绑定顺序展示。
