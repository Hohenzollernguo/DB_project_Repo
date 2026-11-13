package edu.cs307.metro.model;

/** 已有站线资料的一条完整来源；不同线路的属性不混合。 */
public record StopSource(LineView line, StopDraft attributes) {}
