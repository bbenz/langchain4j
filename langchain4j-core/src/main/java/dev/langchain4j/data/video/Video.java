package dev.langchain4j.data.video;

import java.net.URI;
import java.util.Objects;

import static dev.langchain4j.internal.Utils.quoted;

public class Video {

    private final URI url;
    private final String base64Data;
    private final String mimeType;

    /**
     * Create a new {@link Video} from the Builder.
     *
     * @param builder the builder.
     */
    private Video(Builder builder) {
        this.url = builder.url;
        this.base64Data = builder.base64Data;
        this.mimeType = builder.mimeType;
    }

    /**
     * Create a new {@link Builder}.
     *
     * @return the new {@link Builder}.
     */
    public static Builder builder() {
        return new Builder();
    }

    /**
     * Get the url of the video.
     *
     * @return the url of the video, or null if not set.
     */
    public URI url() {
        return url;
    }

    /**
     * Get the base64 data of the video.
     *
     * @return the base64 data of the video, or null if not set.
     */
    public String base64Data() {
        return base64Data;
    }

    /**
     * Get the mime type of the video.
     *
     * @return the mime type of the video, or null if not set.
     */
    public String mimeType() {
        return mimeType;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        Video that = (Video) o;
        return Objects.equals(this.url, that.url)
                && Objects.equals(this.base64Data, that.base64Data)
                && Objects.equals(this.mimeType, that.mimeType);
    }

    @Override
    public int hashCode() {
        return Objects.hash(url, base64Data, mimeType);
    }

    @Override
    public String toString() {
        return "Video {" +
                " url = " + quoted(url) +
                ", base64Data = " + quoted(base64Data) +
                ", mimeType = " + quoted(mimeType) +
                " }";
    }

    /**
     * Builder for {@link Video}.
     */
    public static class Builder {

        private URI url;
        private String base64Data;
        private String mimeType;

        /**
         * Create a new {@link Builder}.
         */
        public Builder() {
        }

        /**
         * Set the url of the video.
         *
         * @param url the url of the video.
         * @return {@code this}
         */
        public Builder url(URI url) {
            this.url = url;
            return this;
        }

        /**
         * Set the url of the video.
         *
         * @param url the url of the video.
         * @return {@code this}
         */
        public Builder url(String url) {
            return url(URI.create(url));
        }

        /**
         * Set the base64 data of the video.
         *
         * @param base64Data the base64 data of the video.
         * @return {@code this}
         */
        public Builder base64Data(String base64Data) {
            this.base64Data = base64Data;
            return this;
        }

        /**
         * Set the mime type of the video.
         *
         * @param mimeType the mime type of the video.
         * @return {@code this}
         */
        public Builder mimeType(String mimeType) {
            this.mimeType = mimeType;
            return this;
        }

        /**
         * Build the {@link Video}.
         *
         * @return the {@link Video}.
         */
        public Video build() {
            return new Video(this);
        }
    }
}
