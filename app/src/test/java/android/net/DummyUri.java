package android.net;

import android.os.Parcel;
import java.util.List;

public final class DummyUri extends Uri {
    private DummyUri() {}
    @Override public boolean isHierarchical() { return true; }
    @Override public boolean isRelative() { return false; }
    @Override public String getScheme() { return "content"; }
    @Override public String getSchemeSpecificPart() { return ""; }
    @Override public String getEncodedSchemeSpecificPart() { return ""; }
    @Override public String getAuthority() { return "media"; }
    @Override public String getEncodedAuthority() { return "media"; }
    @Override public String getUserInfo() { return null; }
    @Override public String getEncodedUserInfo() { return null; }
    @Override public String getHost() { return "media"; }
    @Override public int getPort() { return -1; }
    @Override public String getPath() { return ""; }
    @Override public String getEncodedPath() { return ""; }
    @Override public String getQuery() { return null; }
    @Override public String getEncodedQuery() { return null; }
    @Override public String getFragment() { return null; }
    @Override public String getEncodedFragment() { return null; }
    @Override public List<String> getPathSegments() { return java.util.Collections.emptyList(); }
    @Override public String getLastPathSegment() { return "1"; }
    @Override public String toString() { return "content://media/1"; }
    @Override public Builder buildUpon() { return null; }
    @Override public int describeContents() { return 0; }
    @Override public void writeToParcel(Parcel out, int flags) {}
}
