package todolist;

public enum Category {

    URGENT_IMPORTANT("紧急重要", "#EF4444"),
    URGENT_NOT_IMPORTANT("紧急不重要", "#F59E0B"),
    IMPORTANT_NOT_URGENT("重要不紧急", "#3B82F6");

    private final String label;
    private final String color;

    Category(String label, String color) {
        this.label = label;
        this.color = color;
    }

    public String getLabel() {
        return label;
    }

    public String getColor() {
        return color;
    }

    public static Category fromLabel(String label) {
        for (Category category : values()) {
            if (category.label.equals(label)) {
                return category;
            }
        }
        return URGENT_IMPORTANT;
    }

    @Override
    public String toString() {
        return label;
    }
}