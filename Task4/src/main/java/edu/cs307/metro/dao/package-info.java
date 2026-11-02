/**
 * DAO 接收 Service 的 Connection，执行参数化 SQL并映射 DTO。
 * 自行关闭 PreparedStatement/ResultSet，禁止关闭共享 Connection 或提交事务。
 * 后续按需增加 StationDao、LineDao、LineStopDao、NeighborDao、RouteDao、LandmarkDao；
 * 不提前冻结尚未讨论的内部 DAO 方法签名。
 */
package edu.cs307.metro.dao;
