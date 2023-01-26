package edu.upc.essi.dtim.nextiaqr.models.querying;

import com.google.common.collect.Maps;
import lombok.Data;
import lombok.Getter;
import lombok.Setter;
import org.glassfish.jersey.internal.guava.Sets;
import scala.annotation.meta.getter;

import java.util.Map;
import java.util.Set;

@Getter @Setter
public class RewritingResult {

    Set<ConjunctiveQuery> CQs;
    Map<String,Integer> projectionOrder;
    Map<String,String> featuresPerAttribute;

    public RewritingResult() {
        CQs = Sets.newHashSet();
        projectionOrder = Maps.newHashMap();
        featuresPerAttribute = Maps.newHashMap();
    }


}
