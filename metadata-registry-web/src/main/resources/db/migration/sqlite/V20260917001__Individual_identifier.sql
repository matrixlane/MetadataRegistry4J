-- Individual 应用扩展属性 identifier（非 ISO/IEC 11179-3 标准属性）。
alter table individual add column identifier text;
update individual set identifier = 'individual-' || lower(hex(randomblob(4))) where identifier is null;
