// if you don't specify a html file, the sniper will generate a div with id "rootDiv"


//An array of colours which are used for the different probes

// uncharged polar side chains, then Basic side chains, acidic, the hydrophobic





/* Tip which is displayed when hovering over the nodes */
var tip = d3.tip()
        .attr('class', 'd3-tip')
        .offset([0, +110])
        .html(function (d) {
            //label = d.label;
            temp =
                    "Sample Type: <br/>"
            return temp;
        });

var setup_options = function (svg_id, json_str) {
    var data = JSON.parse(json_str);
    //The main options for the graph
    var options = {
        number_of_edges_to_be_interesting: 2, // Min number of edges to consider it interesting

        /******** Options for node and edge drawing ***************************/
        graphs_display: true, // Graphs will only display if the data contains
        // graph information -> note histogram information must be there for all
        // node
        seq_display: true, // This is if there is sequence data in the node

        /**************** Options for the lanes (used when showing both an inferred and raw POAG)   **/
        depth: 3, // Indicates how many layers for the nodes
        lane_height: 20, // A "lane" is the region where a POAG is drawn this is
        lane_padding: 40,
        mini_radius: 7,
        interesting_many_edges_colour: "MidnightBlue",
        diff_colour: "CornflowerBlue",
        diff_opacity: 0.2,
        num_start_nodes : 6,// How many nodes that it starts with
        x_padding: 100, // the padding to the left of teh first node in relation to the y axis titles
        multi: {
            main_height: 400,
            mini_height: 20
        },
         /**************** Options for changing the style of the nodes *************************/
        node: {
            stroke_width: 2,
            stroke: "#d3d3d3",
            hover_radius: 10,
            text_size: "18px",
            font_family: "Gill Sans, sans-serif",
            text_padding: 5,
            font_colour: "#233",
            gradient: false,
            x_size: 150, // Multiplication factor for positions
            y_size: 100,
            x_start: 200, // where to put the first node
            y_start: 400
        },
        /**************** Options for style of the edges between nodes **********************/
        edge: {
            y_curve_amount: 25,
            stroke_width: 3,
            stroke: "grey",
            stroke_opacity: 1,
            x_length: 160,
            text_size: "12px",
            font_family: "Gill Sans, sans-serif",
            font_color: "grey",
            text_stroke_width: 1,
            y_length: 70

        },
        /********** Pie chart options ***************************************/
        pie: {
            label_position: 22, // Makes the label this distance in from the outer radius
            radius: 50,
            stroke_width: 3,
            stroke: "white",
            text_size: "12px",
            font_family: "Gill Sans, sans-serif",
            max_seq_len: 6 // Number of sequences in a pie chart where we don't draw the divding lines between (stroke
                           // width gets set to 0;
        },
        /*********** Histogram options  ************************************/
        graph: {
            x_size: 150, // Multiplication factor for positions
            y_size: 100,
            y_start: 400,
            x_start: 200,
            svg_overlay: null,
            x: 0,
            y: 0,
            x_label_array: ['one', 'two'],
            graph_outer_circle_colour: "blue",
            graph_outer_circle_opacity: 0.5,
            graph_outer_circle_radius: 65,
            graphs: new Array(),
            size: 60,
            colours: random, // default to random colour scheme
            offset_graph_width: -29,
            offset_graph_height: -15,
            width: 80,
            div_factor: 1,
            graph_height: 50,
            max_height: 10,
            max_bar_count: 2,
            hover: true,
            metabolite_count: 0
        },
        /******** Options for Sizing *****************************************/
        legend_padding: 0,
        legend_rect_size: 0,
        height: 800,
        width: 2000,
        margin: {top: 50, left: 150, bottom: 50, right: 50},
        initial_padding: 0,
        colours: random,
        svg_padding: 20, // The padding of the svg within the div given
        padding_between_views: 50,
        /*********** End of sizing options **********************************/
        background_colour: "white",
        background_stroke_colour: "black",
        background_stroke_width: "1px",
        stroke_width: "3px",
        /********** Data ***************************************************/
        data: data,
        raw_svg_id: svg_id,
        target: '#' + svg_id,
        /********** Text options ******************************************/
        font_style: "Arial",
        text_size: "12px",
        title: "Alignment Vis Tool",
        title_text_size: "16px",
        tip: tip, //second tip to just display the sample type
        x_axis_text_angle: -45,
        x_axis_title: "Samples",
        x_column: 'Sample_ID',
        x_middle_title: 500,
        y_column: 'Expression_Value',
        y_label_text_size: "14px",
        y_label_x_val: 40,
    };

    return options;
};