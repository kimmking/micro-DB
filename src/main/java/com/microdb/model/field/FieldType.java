package com.microdb.model.field;

/**
 * 字段数据类型
 *
 * @author zhangjw
 * @version 1.0
 */
public enum FieldType implements IFieldType {
    INT() {
        @Override
        public int getSizeInByte() {
            return 4;
        }
    }
}
