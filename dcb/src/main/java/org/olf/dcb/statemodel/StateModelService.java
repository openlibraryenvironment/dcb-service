package org.olf.dcb.statemodel;

import static guru.nidi.graphviz.attribute.Attributes.attr;
import static guru.nidi.graphviz.attribute.GraphAttr.splines;
import static guru.nidi.graphviz.attribute.Rank.RankDir.TOP_TO_BOTTOM;
import static guru.nidi.graphviz.model.Factory.graph;
import static guru.nidi.graphviz.model.Factory.node;
import static guru.nidi.graphviz.model.Link.to;

import java.io.ByteArrayOutputStream;
import java.util.ArrayList;
import java.util.List;

import org.olf.dcb.core.model.PatronRequest.Status;
import org.olf.dcb.request.workflow.PatronRequestStateTransition;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import guru.nidi.graphviz.attribute.Color;
import guru.nidi.graphviz.attribute.Font;
import guru.nidi.graphviz.attribute.GraphAttr.SplineMode;
import guru.nidi.graphviz.attribute.Rank;
import guru.nidi.graphviz.attribute.Shape;
import guru.nidi.graphviz.engine.Format;
import guru.nidi.graphviz.engine.Graphviz;
import guru.nidi.graphviz.model.Graph;
import guru.nidi.graphviz.model.LinkSource;
import io.micronaut.context.annotation.Prototype;

@Prototype
public class StateModelService {

	private static final Logger log = LoggerFactory.getLogger(StateModelService.class);

	public static final String FORMAT_DOT = "dot";
    public static final String FORMAT_PNG = "png";
    public static final String FORMAT_SVG = "svg";

    private static final List<Status> redStates = List.of(Status.ERROR, Status.FINALISED);

    private final List<PatronRequestStateTransition> allTransitions;
    
	public StateModelService(List<PatronRequestStateTransition> allTransitions) {
    	this.allTransitions = allTransitions;
	}

    public byte[] generateGraph(
        String format
    ) {
    	byte [] result = new byte[0];
        try {
            // Now build up the list of the links
            // First the actions
            List<LinkSource> links = new ArrayList<LinkSource>();
            allTransitions.forEach((transition) -> {
                buildLinks(links, transition, Color.PURPLE, Color.BLACK, redStates);
            });

            // Now we can define our graph
            Graph stateTransitionsGraph = graph("State Transitions")
                .directed()
                .graphAttr().with(Rank.dir(TOP_TO_BOTTOM))
                .graphAttr().with(splines(SplineMode.POLYLINE))
                .nodeAttr().with(Font.name("arial"))
                .linkAttr().with("class", "link-class")
                .with(links);

            try {
                Format renderFormat = Format.DOT;
                if (format != null) {
                    switch (format) {
                        case FORMAT_SVG:
                            renderFormat =  Format.SVG;
                            break;

                        case FORMAT_PNG:
                            renderFormat =  Format.PNG;
                            break;

                        case FORMAT_DOT:
                        default:
                            // Already set as dot on initialising the variable
                            break;
                    }
                }

                // Create ourselves a new byte array stream
                ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
                
                // Now we have determined the format, render the output
                Graphviz.fromGraph(stateTransitionsGraph).height(2000).render(renderFormat).toOutputStream(outputStream);
                
                // Tyrn the stream into a byte array
                result = outputStream.toByteArray();
            }
            catch (Exception e) {
                log.error("Exception thrown while building chart", e);
            }
        } catch (Exception e) {
            log.error("Exception thrown while generating chart", e);
        }
        
        // Return the result to the caller
        return(result);
    }

    @SuppressWarnings("unchecked")
	private void buildLinks(
    	List<LinkSource> links,
    	PatronRequestStateTransition transition,
    	Color linkLineColor,
    	Color stateColour,
    	List<Status> completedStates
    ) {
        // Must have From and To states
    	List<Status> fromStates = transition.getPossibleSourceStatus();
    	if (transition.getTargetStatus().isPresent()) {
	    	Status toState = transition.getTargetStatus().get();
	
	    	
	    	if ((toState != null) && (fromStates != null) && !fromStates.isEmpty()) {
	            // We need to build a link between each from and to state and if the to node is completed we then make it RED
	    		fromStates.forEach((fromState) -> {
	                String linkName = fromState.toString() + " => " + toState.toString() + " (" + transition.getClass().getSimpleName() + ")";
	
	                // Is this a completed, in which case we want a different colour
	                Boolean iscompletedState =  completedStates.contains(toState);
	
	                // Initialise the colour of the line and shape of the TO node, shape of the FROM node is always box
	                Color toNodeColor = stateColour;
	                Color linkColor = linkLineColor;
	                Shape toNodeShape = Shape.BOX;
	
	                // If its a completed state TO node, then we override the colours and shape
	                if (iscompletedState) {
	                    toNodeColor = Color.RED;
	                    linkColor = Color.RED;
	                    toNodeShape = Shape.OVAL;
	                }
	
	                // Now we create the link between the from and to states
	                links.add(node(fromState.toString()).with(Shape.BOX).link(
	                    to(
	                    	node(toState.toString())
	                    		.with(toNodeShape)
	                    		.with(toNodeColor)
	                    ).with(
	                    	linkColor,
	                    	attr("decorate", true),
	                    	attr("weight", 5),
	                    	attr("label", ""),
	                    	attr("tooltip", linkName)
	                    ).with(
	                    	linkColor,
	                    	attr("decorate", true),
	                    	attr("weight", 5),
	                    	attr("label", ""),
	                    	attr("tooltip", linkName)
	                    )
	                ));
	    		});
	    	} else {
	        	log.debug("No target or source status for transition: " + transition.getName());
	    	}
        } else {
        	log.debug("No target status for transition: " + transition.getName());
        }
	}
}
