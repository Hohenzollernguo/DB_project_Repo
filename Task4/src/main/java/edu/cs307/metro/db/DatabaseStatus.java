package edu.cs307.metro.db;

/** 只用于连接诊断；不包含用户名、密码或带连接参数的 URL。 */
public record DatabaseStatus(String database, String schema, String serverVersion) {}
