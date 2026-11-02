package edu.cs307.metro.db;

/** 23503 必须结合上下文：删除被引用记录与写入不存在的引用，含义不同。 */
public enum SqlOperation { READ, WRITE, DELETE }
