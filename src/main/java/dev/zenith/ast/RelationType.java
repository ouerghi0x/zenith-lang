package dev.zenith.ast;

public enum RelationType {
    MANY_TO_ONE,
    ONE_TO_MANY,
    ONE_TO_ONE,
    MANY_TO_MANY;

    public static RelationType fromString(String str) {
        if (str == null) return MANY_TO_ONE;
        String s = str.trim().toLowerCase();
        switch (s) {
            case "manytoone":
            case "many_to_one":
                return MANY_TO_ONE;
            case "onetomany":
            case "one_to_many":
                return ONE_TO_MANY;
            case "onetoone":
            case "one_to_one":
                return ONE_TO_ONE;
            case "manytomany":
            case "many_to_many":
                return MANY_TO_MANY;
            default:
                throw new IllegalArgumentException("Unknown relation type: " + str);
        }
    }
}
