package scot.gov.publications.metadata;

import com.fasterxml.jackson.annotation.JsonFormat;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.fasterxml.jackson.datatype.jsr310.ser.LocalDateTimeSerializer;
import org.apache.commons.lang3.StringUtils;

import java.time.LocalDateTime;
import java.time.ZonedDateTime;

@JsonIgnoreProperties(ignoreUnknown = true)
public class Metadata {

    String id;

    String wpid;

    String title;

    @JsonSerialize(using= LocalDateTimeSerializer.class)
    @JsonFormat(shape = JsonFormat.Shape.STRING, pattern = "yyyy-MM-dd'T'HH:mm:ss")
    LocalDateTime publicationDate;

    ZonedDateTime publicationDateWithTimezone;

    String url;

    String alternateUrl;

    String executiveSummary;

    String description;

    String isbn;

    String topic;

    String publicationType;

    String keywords;

    String researchCategory;

    String statisticsCategory;

    EqualityInfo equalityInfo;

    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    public String getWpid() {
        return wpid;
    }

    public void setWpid(String wpid) {
        this.wpid = wpid;
    }

    public String getTitle() {
        return title;
    }

    public void setTitle(String title) {
        this.title = title;
    }

    public LocalDateTime getPublicationDate() {
        return publicationDate;
    }

    public void setPublicationDate(LocalDateTime publicationDate) {
        this.publicationDate = publicationDate;
    }

    public ZonedDateTime getPublicationDateWithTimezone() {
        return publicationDateWithTimezone;
    }

    public void setPublicationDateWithTimezone(ZonedDateTime publicationDateWithTimezone) {
        this.publicationDateWithTimezone = publicationDateWithTimezone;
    }

    public String getUrl() {
        return url;
    }

    public void setUrl(String url) {
        this.url = url;
    }

    public String getAlternateUrl() {
        return alternateUrl;
    }

    public void setAlternateUrl(String alternateUrl) {
        this.alternateUrl = alternateUrl;
    }

    public String getExecutiveSummary() {
        return executiveSummary;
    }

    public void setExecutiveSummary(String executiveSummary) {
        this.executiveSummary = executiveSummary;
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    public String getIsbn() {
        return isbn;
    }

    public void setIsbn(String isbn) {
        this.isbn = isbn;
    }

    public String getTopic() {
        return topic;
    }

    public void setTopic(String topic) {
        this.topic = topic;
    }

    public String getPublicationType() {
        return publicationType;
    }

    public void setPublicationType(String publicationType) {
        this.publicationType = publicationType;
    }

    public String getKeywords() {
        return keywords;
    }

    public void setKeywords(String keywords) {
        this.keywords = keywords;
    }

    public String getResearchCategory() {
        return researchCategory;
    }

    public void setResearchCategory(String researchCategory) {
        this.researchCategory = researchCategory;
    }

    public String getStatisticsCategory() {
        return statisticsCategory;
    }

    public void setStatisticsCategory(String statisticsCategory) {
        this.statisticsCategory = statisticsCategory;
    }

    public EqualityInfo getEqualityInfo() {
        return equalityInfo;
    }

    public void setEqualityInfo(EqualityInfo equalityInfo) {
        this.equalityInfo = equalityInfo;
    }

    public String normalisedIsbn() {
        return StringUtils.isEmpty(isbn)
                ? ""
                : isbn.replaceAll("\\s", "").replaceAll("-", "");
    }
}
