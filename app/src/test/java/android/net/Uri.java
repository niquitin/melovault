package android.net;

import java.io.File;
import java.net.URI;

public abstract class Uri implements Comparable<Uri> {

    public abstract String getScheme();
    public abstract String getPath();
    public abstract String toString();
    public abstract Builder buildUpon();

    public static Uri parse(String uriString) {
        return new StringUri(uriString);
    }

    public static Uri fromFile(File file) {
        return new StringUri(file.toURI().toString());
    }

    @Override
    public int compareTo(Uri o) {
        return toString().compareTo(o.toString());
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof Uri)) return false;
        return toString().equals(o.toString());
    }

    @Override
    public int hashCode() {
        return toString().hashCode();
    }

    public static class Builder {
        private String uriString;

        public Builder(String uriString) {
            this.uriString = uriString;
        }

        public Builder clearQuery() {
            int qIndex = uriString.indexOf('?');
            if (qIndex >= 0) {
                uriString = uriString.substring(0, qIndex);
            }
            return this;
        }

        public Builder appendQueryParameter(String key, String value) {
            char sep = uriString.contains("?") ? '&' : '?';
            uriString = uriString + sep + key + "=" + value;
            return this;
        }

        public Uri build() {
            return new StringUri(uriString);
        }
    }

    private static class StringUri extends Uri {
        private final String uriString;

        StringUri(String uriString) {
            this.uriString = uriString;
        }

        @Override
        public String getScheme() {
            try {
                return URI.create(uriString).getScheme();
            } catch (Exception e) {
                int colon = uriString.indexOf(':');
                return colon > 0 ? uriString.substring(0, colon) : null;
            }
        }

        @Override
        public String getPath() {
            try {
                return URI.create(uriString).getPath();
            } catch (Exception e) {
                return null;
            }
        }

        @Override
        public String toString() {
            return uriString;
        }

        @Override
        public Builder buildUpon() {
            return new Builder(uriString);
        }
    }
}
