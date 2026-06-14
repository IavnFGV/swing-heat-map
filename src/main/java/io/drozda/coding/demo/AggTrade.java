package io.drozda.coding.demo;

record AggTrade(
        long id,
        double price,
        double quantity,
        long timestampMicros,
        boolean buyerMaker
) {
    boolean buyerAggressor() {
        return !buyerMaker;
    }
}
