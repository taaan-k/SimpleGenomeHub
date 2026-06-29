package simplegenomehub.util.MultipleSynteny;

public enum MultipleSyntenyAnchorMode {
    LEFT("Left"),
    CENTER("Center"),
    RIGHT("Right");

    private final String label;

    MultipleSyntenyAnchorMode(String label) {
        this.label = label;
    }

    public String getLabel() {
        return label;
    }
}
