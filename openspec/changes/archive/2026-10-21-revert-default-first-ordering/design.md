# Design: revert-default-first-ordering

删除 `ContactService.listMyContacts` 的默认置顶稳定排序（恢复绑定顺序），测试断言改回绑定顺序。默认驱动的激活解析（activation-follows-default）与本排序无关，保持不变。
