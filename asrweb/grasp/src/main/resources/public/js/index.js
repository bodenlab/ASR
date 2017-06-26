/*
 * http://bl.ocks.org/bunkat/1962173
 */
var graph = {};

// keep track of window view
var prevbrushStart = -1;
var prevbrushEnd = -1;
var retain_previous_position = false;

setup_data = function (graph) {
    var lanes = [];
    var nodes = [];
    var node_dict = {};
    var node_many_edge_dict = {}; // Keeps track of nodes with > 1 edge coming out
    var edges = [];
    var poags = graph.data;
    var current_y_position = 0;
    var count = 0;
    var total_max_depth = 0;
    var max_seq_len = 0;
    for (var poag_count = 0; poag_count < 2; poag_count ++) {
        if (poag_count == 0) {
            // Choose the poag which is to be on the top
            var poag = poags.top;
            // The poag type determines whether or not the pie charts a drawn
            var poag_type = poag.metadata.type;
        } else {
            // Choose the poag which is to be on the bottom
            var poag = poags.bottom;
            // The poag type determines whether or not the pie charts a drawn
            var poag_type = poag.metadata.type;
        }
        var title = poag.metadata.title;
        var max_depth = poag.max_depth;
        if (max_depth > total_max_depth) {
            total_max_depth = max_depth;
        }
        for (var i = 0; i <= max_depth; i++) {
            var temp = {};
            temp.id = count;
            temp.label = title + ", depth: " + i;
            lanes.push(temp);
            count++;
        }
        // Add each of the nodes to the items
        for (var n in poag.nodes) {
            var node = poag.nodes[n];
            if (poag_count == 0) {
                // Add the type to the node so we know whether or not to draw the distributions
                node.type = poag_type;
                node.deleted_during_inference = true;
                node.inferred = false;
                node.mutant = false;
                node.msa = true;
                node.many_edges = false;
                node.start = node.x;
                node.end = node.x;
                if (node.seq.chars.length > max_seq_len) {
                    max_seq_len = node.seq.chars.length;
                }
                node.graph = {};
                if (mutants > 0) {
                    node.graph.bars = node.mutants.chars;
                } else {
                    node.graph.bars = node.seq.chars;
                }
                // Assume that every node has been deleted during the ineference process
                node_dict[node.id] = node;
            } else {
                if (n == 0) {
                    node.first_node = true; // Used to make the line for the mini line
                }
                var node_inferred = node_dict[node.id];
                // Update the x coords to match that of the MSA node (to account for deletions)
                node.start = node_inferred.start;
                node.msa = false;
                node.mutant = false;
                if (node.inferred == true && node.mutants.chars.length > 0) {
                    node.mutant = true;
                }
                // Add the type to the node so we know whether or not to draw the distributions
                node.type = poag_type;
                node.x = node_inferred.start;
                node.end = node_inferred.end;
                node.graph = {};
                if (mutants > 0) {
                    node.graph.bars = node.mutants.chars;
                } else {
                    node.graph.bars = node.seq.chars;
                }
                node.inferred = true;
                node.many_edges = false;
                // Update to say that it hasn't been deleted since it appears in both
                node.deleted_during_inference = false;
                node_inferred.deleted_during_inference = false;

            }
            node.y += current_y_position;
            node.lane += current_y_position;
            nodes.push(node);
        }
        // Add each of the reactions to the reaction items
        for (var e in poag.edges) {
            var edge = poag.edges[e];
            edge.y1 += current_y_position;
            edge.y2 += current_y_position;
            // Update the to and from ID's for the nodes to include the POAG
            // if we are in the inferred version we need to get the updated x coods as above
            var node_inferred_from = node_dict[edge.from];
            var node_inferred_to = node_dict[edge.to];
            edge.x1 = node_inferred_from.start;
            edge.x2 = node_inferred_to.start;
            edge.from = edge.from;
            edge.to = edge.to;
            edges.push(edge);
            // Check if the node that the edge is coming from already has edges out of it (we are summing these
            // and determining if it can be considered interesting)
            if (poag_count == 0) {
                var edges_from_node =  node_many_edge_dict[edge.from];
                if ( edges_from_node == undefined) {
                edges_from_node = new Array();
                }
                edges_from_node.push(edge);
                node_many_edge_dict[edge.from] = edges_from_node;
                if (edges_from_node.length >= graph.options.number_of_edges_to_be_interesting) {
                    // tag the from node to be interesting
                    node_dict[edge.from].many_edges = true;
                }
            }
        }
        current_y_position += (max_depth + 1);
    }
    graph.max_depth = total_max_depth + 1;
    graph.lanes = lanes;
    graph.nodes = nodes;
    // So we always have all the nodes available
    graph.all_nodes = nodes;
    graph.edges = edges;
    graph.node_count = count;
    graph.node_diff = node_dict;
    graph.max_seq_len = max_seq_len;
    return graph;
};

make_scales = function (graph) {
    var nodes = graph.nodes;
    var lanes = graph.lanes;
    var radius = graph.max_radius;
    var options = graph.options;
    var margin = options.margin;
    var x_padding = options.x_padding;

    var width = options.width - margin.left - margin.right
            , height = options.height - margin.top - margin.bottom
            , miniHeight = lanes.length * options.lane_height + options.lane_padding
            , mainHeight = options.height - miniHeight - options.lane_padding;

    var x = d3.scale.linear()
            .domain([(d3.min(nodes, function (d) {
                    return d.start - 1;
                })),
                d3.max(nodes, function (d) {
                    return d.end + 1;
                })])
            .range([0, width]);


    var x1 = d3.scale.linear().range([0, width]);

    var ext = d3.extent(lanes, function (d) {
        return d.id;
    });
    var y1 = d3.scale.linear().domain([ext[0], ext[1] + 1]).range([0, mainHeight]);
    var y2 = d3.scale.linear().domain([ext[0], ext[1] + 1]).range([0, miniHeight]);

    // Place the mini viewer below the main graph and the padding between the views
    //graph.position_of_mini_viewer = options.padding_between_views + mainHeight;
    graph.scale = {};
    graph.scale.x1 = x1;
    graph.scale.x = x;
    graph.scale.y1 = y1;
    graph.scale.y2 = y2;
    graph.scale.ext = ext;
    graph.page_options = {};
    graph.page_options.width = width;
    graph.page_options.margin = margin;
    graph.page_options.height = height;
    graph.page_options.miniHeight = miniHeight;
    graph.page_options.mainHeight = mainHeight;
    return graph;
}

setup_svg = function (graph) {
    var nodes = graph.nodes;
    var lanes = graph.lanes;
    var margin = graph.page_options.margin;
    var width = graph.page_options.width;
    var height = graph.page_options.height;
    var mainHeight = graph.page_options.mainHeight;
    var miniHeight = graph.page_options.miniHeight;
    var x1 = graph.scale.x1;
    var y1 = graph.scale.y1;
    var x = graph.scale.x;
    var y2 = graph.scale.y2;
    var options = graph.options;

    var max_depth = graph.max_depth;
    // Get the width of the DIV that we are appending the svg to so we can scale the height and width values
    var actual_svg_width = document.getElementById(options.raw_svg_id).offsetWidth;
    var actual_svg_height =  document.getElementById(options.raw_svg_id).offsetHeight;
    // We don't want to get the height as we only want to develop the scale based on one element
    var scale_width = 0.9;// Want it to be 10% smaller than the space to add even padding
    var scale_height = 0.9;

    var width = width;
    var padding = scale_width *(options.svg_padding);

    var general_svg = d3.select(options.target)
            .append("svg")
            //.attr("preserveAspectRatio", "xMinYMin meet")
            //.attr("preserveAspectRatio", "xMaxYMax meet")
            .attr("viewBox", "0 0 " + actual_svg_width + " " + actual_svg_height)
            .classed("svg-content", true);

    var chart = general_svg.append('g')
                    .attr("transform",  "translate(" + 0 + ",-" + options.svg_padding + ")" + " scale(1)");// + scale_width + ")");

    chart.append('defs').append('clipPath')
            .attr('id', 'clip')
            .append('rect')
            .attr('width', width)
            .attr('height', mainHeight / max_depth);

    var main = chart.append('g')
            .attr('transform', 'translate(' + margin.left + ',' + margin.top + ')')
            .attr('width', width)
            .attr('height', mainHeight)
            .attr('class', 'main');

    var mini = chart.append('g')
            .attr('transform', 'translate(' + margin.left/2 + ',' + (mainHeight + options.padding_between_views) + ')')
            .attr('width', width)
            .attr('height', miniHeight - options.padding_between_views)
            .attr('class', 'mini');

    graph.mini = mini;
    graph.main = main;
    options.graph.svg_overlay = main;
    return graph;
};

    // https://github.com/wbkd/d3-extended
    d3.selection.prototype.moveToFront = function() {
      return this.each(function(){
        this.parentNode.appendChild(this);
      });
    };
    d3.selection.prototype.moveToBack = function() {
        return this.each(function() {
            var firstChild = this.parentNode.firstChild;
            if (firstChild) {
                this.parentNode.insertBefore(this, firstChild);
            }
        });
    };

setup_items = function (graph) {
    var mini = graph.mini;
    var main = graph.main;
    // draw the items
    var node_group = main.append('g');

    mini.append('g').selectAll('miniItems')
            .data(getPaths(graph))
            .enter().append('path')
            .attr('class', function (d) {
                return 'miniItem ' + d.class;
            })
            .attr('d', function (d) {
                return d.path;
            });
    graph.node_group = node_group;
    return graph;
}

setup_brush = function (graph) {
    var width = graph.page_options.width;
    var mainHeight = graph.page_options.mainHeight;
    var miniHeight = graph.page_options.miniHeight;
    var x = graph.scale.x;
    var y_scale = graph.scale.y2;
    var mini = graph.mini;
    var main = graph.main;
    var options = graph.options;

    // invisible hit area to move around the selection window
    mini.append('rect')
            .attr('pointer-events', 'painted')
            .attr('width', width)
            .attr('height', miniHeight)
            .attr('visibility', 'hidden')
            .on('mouseup', moveBrush);

    // draw the selection area
    var brush = d3.svg.brush()
            .x(x)
            .extent([0, options.num_start_nodes])
            .on("brush", display);


    mini.append('g')
            .attr('class', 'x brush')
            .call(brush)
            .selectAll('rect')
            .attr('y',  y_scale(0) - 10)
            .attr('height', miniHeight + options.lane_height + 20);

    mini.selectAll('rect.background').remove();

    graph.brush = brush;
    return graph;

};

create_poags = function (options) {
    // Stores everything for the graph

    graph.options = options;
    var data = options.data;
    graph.data = data;
    graph = setup_data(graph);
    // Make the radius based on the graph height and the number of lanes
    graph.options.graph.colours = options.colours;
    graph.max_radius = (options.height / graph.lanes.length) / 3;
    // Where the minimum size of the radius is set up to determine when to stop drawing labels
    graph.min_radius = graph.max_radius / 3;
    graph = make_scales(graph);
    graph = setup_svg(graph);
    graph = setup_items(graph);
    graph = draw_mini_line(graph);//draw_mini_nodes(graph);
    graph = setup_brush(graph);
    display();
};


function display() {
    var brush = graph.brush;
    var nodes_curr = graph.nodes;

    var mini = graph.mini;

    var x_scale = graph.scale.x1;

    var options = graph.options;

    var minExtent = (brush.extent()[0]);
    var maxExtent = (brush.extent()[1]);
    if (prevbrushStart == -1 || retain_previous_position == false) {
         prevbrushStart = minExtent;
         prevbrushEnd = maxExtent;
    }
    retain_previous_position = false;

    var vis_nodes = nodes_curr.filter(function (d) {
            return d.start < prevbrushEnd && d.end >= prevbrushStart - 1;
        });

    mini.select('.brush').call(brush.extent([prevbrushStart, prevbrushEnd]));

    x_scale.domain([prevbrushStart, prevbrushEnd]);

    // Delete all the old edges
    graph.node_group.selectAll("path.edge").remove();
    graph.node_group.selectAll("path.pie").remove();

    // Delete all graphs
    graph.options.graph.svg_overlay.selectAll("g.graph").remove();

    // Delete all old nodes
    graph.node_group.selectAll("circle.main_node").remove();

    // Delete all the old text
    graph.node_group.selectAll("text.edge_text").remove();
    graph.node_group.selectAll("text.node_text").remove();
    graph.node_group.selectAll("text.pie").remove();
    graph.node_group.selectAll("text.position_text").remove();

    draw_nodes(graph, vis_nodes, prevbrushStart, prevbrushEnd);
    draw_node_edges(graph, prevbrushStart, prevbrushEnd);
    draw_positions(graph, vis_nodes, prevbrushStart, prevbrushEnd);

}


function moveBrush() {
    var brush = graph.brush;
    var origin = d3.mouse(this)
            , point = graph.scale.x.invert(origin[0])
            , halfExtent = (brush.extent()[1] - brush.extent()[0]) / 2
            , start = point - halfExtent
            , end = point + halfExtent;
    console.log(brush);
    graph.brush.extent([start, end]);
    prevbrushStart = start;
    prevbrushEnd = end;
    retain_previous_position = true;
    display();
}


// generates a single path for each item class in the mini display
// ugly - but draws mini 2x faster than append lines or line generator
// is there a better way to do a bunch of lines as a single path with d3?
function getPaths(graph) {
    var items = graph.nodes;
    var x = graph.scale.x;
    var y2 = graph.scale.y2;
    var paths = {}, d, offset = .5 * y2(1) + 0.5, result = [];
    for (var i = 0; i < items.length; i++) {
        d = items[i];
        if (!paths[d.class])
            paths[d.class] = '';
        paths[d.class] += ['M', x(d.start), (y2(d.lane) + offset), 'H', x(d.end)].join(' ');
    }

    for (var className in paths) {
        result.push({class: className, path: paths[className]});
    }

    return result;
}

/*
** Refresh the graph to be the latest reconstructed
*/
var refresh_graphs = function(options) {
    d3.select(".svg-content").remove();
    retain_previous_position = true;
    options = create_poags(options);
};