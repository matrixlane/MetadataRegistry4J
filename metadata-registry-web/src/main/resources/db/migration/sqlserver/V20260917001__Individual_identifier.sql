-- Individual 应用扩展属性 identifier（非 ISO/IEC 11179-3 标准属性）。
alter table individual add identifier nvarchar(255);
update individual set identifier = 'individual-' + lower(substring(convert(varchar(36), newid()), 1, 8)) where identifier is null;
