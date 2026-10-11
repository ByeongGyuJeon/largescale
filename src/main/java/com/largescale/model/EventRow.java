package com.largescale.model;

import java.math.BigDecimal;

public record EventRow(long id, long userId, String event, BigDecimal value, String payload) { }
