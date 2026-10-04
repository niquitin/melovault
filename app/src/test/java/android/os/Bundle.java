package android.os;

public class Bundle extends BaseBundle implements Cloneable {
    public static final Bundle EMPTY = new Bundle();

    public Bundle() {
        super();
    }

    public Bundle(Bundle b) {
        super();
        if (b != null) {
            this.mMap.putAll(b.mMap);
        }
    }

    public Bundle deepCopy() {
        return new Bundle(this);
    }

    @Override
    public Object clone() {
        return new Bundle(this);
    }
}
