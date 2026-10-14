package com.largescale.model;

import java.math.BigDecimal;

public record EventRow(long id, String userId, String event, BigDecimal value, String payload) { }
