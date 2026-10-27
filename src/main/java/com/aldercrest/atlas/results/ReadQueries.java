package com.aldercrest.atlas.results;

import com.aldercrest.atlas.clinical.Analyte;
import java.time.Instant;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.Map;
import java.util.Set;

/** Parse within the audited action, not by pre-transaction MVC binding. No input in error text. */
public final class ReadQueries {
    private ReadQueries() { }
    public static final class InvalidQuery extends IllegalArgumentException {
        private InvalidQuery() {super("INVALID_QUERY");}
    }
    public record Page(int offset,int limit) { }
    public record Trend(Analyte analyte,Instant from,Instant to) {
        public boolean contains(Instant time) {return (from==null || !time.isBefore(from)) && (to==null || !time.isAfter(to));}
    }
    public static Page page(Map<String,String[]> parameters) {
        check(parameters,Set.of("offset","limit"));
        return new Page(integer(parameters,"offset",0,0,1_000_000),integer(parameters,"limit",50,1,200));
    }
    public static Trend trend(Map<String,String[]> parameters) {
        check(parameters,Set.of("code","from","to"));
        String code=single(parameters,"code");
        if(code==null || code.length()>64) {throw invalid();}
        Analyte analyte;
        try {analyte=Analyte.fromCode(code);} catch(com.aldercrest.atlas.clinical.ClinicalInputException error) {throw invalid();}
        Instant from=bound(single(parameters,"from"),false),to=bound(single(parameters,"to"),true);
        if(from!=null && to!=null && from.isAfter(to)) {throw invalid();}
        return new Trend(analyte,from,to);
    }
    public static void none(Map<String,String[]> parameters) {check(parameters,Set.of());}
    private static int integer(Map<String,String[]> parameters,String name,int defaultValue,int minimum,int maximum) {
        String text=single(parameters,name);
        if(text==null) {return defaultValue;}
        if(!text.matches("[0-9]{1,7}")) {throw invalid();}
        int number=Integer.parseInt(text);
        if(number<minimum || number>maximum) {throw invalid();}
        return number;
    }
    private static Instant bound(String text,boolean upper) {
        if(text==null) {return null;}
        if(text.length()>64) {throw invalid();}
        try {
            if(text.matches("[0-9]{4}-[0-9]{2}-[0-9]{2}")) {
                LocalDate date=LocalDate.parse(text);
                return upper?date.plusDays(1).atStartOfDay().toInstant(ZoneOffset.UTC).minusNanos(1):date.atStartOfDay().toInstant(ZoneOffset.UTC);
            }
            return OffsetDateTime.parse(text).toInstant();
        } catch(java.time.DateTimeException error) {throw invalid();}
    }
    private static void check(Map<String,String[]> parameters,Set<String> allowed) {
        if(parameters==null || !allowed.containsAll(parameters.keySet())) {throw invalid();}
        parameters.forEach((name,values) -> {if(values==null || values.length!=1 || values[0]==null || values[0].isEmpty()) {throw invalid();}});
    }
    private static String single(Map<String,String[]> parameters,String name) {return parameters.containsKey(name)?parameters.get(name)[0]:null;}
    private static InvalidQuery invalid() {return new InvalidQuery();}
}
