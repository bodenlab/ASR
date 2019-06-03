/**
 * The file that facilitates downloading any of the data from GRASP.
 **/

let downloadData = function(btnId) {
  if (btnId === 'download-joint') {
    downloadGet('/download-ancs', 'joint');
  } else if (btnId === 'download-tree') {
    downloadGet('/download-tree');
  } else if (btnId === 'download-marginal') {
    downloadGet('/download-ancs', 'marginal');
  }
};

let downloadGet = function(url, dataPost) {
  document.getElementById("download-error-div").style.display = "none";
  // Open the modal modal from https://codepen.io/luv2code/pen/evaBXm
  $("#loadMe").modal({
    backdrop: "static", //remove ability to close modal with click
    show: true //Display loader!
  });

  let req = {
    url: url,
    type: 'GET',
    contentType: "application/json",
    dataType: 'json',
    success: function (data) {
      if (data.error !== undefined) {
        $("#loadMe").modal("hide");
        window.alert(data.error);
      } else {
        // Now we want to download the data as a file.
        let filename = data.filename;
        let fileType = data.filetype;
        let fileContent = data.filecontent;
        $("#loadMe").modal("hide");
        if (fileContent === "") {
          if (dataPost === "marginal") {
            let err = "No marginal reconstructions have been saved, please save some marginal reconstructions and try again :)";
            document.getElementById("download-error").innerText = err;
            document.getElementById("download-error-div").style.display = "block";

            window.alert(err);
          } else {
            let err = "Looks like you need to save your reconstruction, click the save button in the top left corner :)";
            document.getElementById("download-error").innerText = err;
            document.getElementById("download-error-div").style.display = "block";

            window.alert(err);
            return;
          }
        } else {
          download(fileContent, filename, fileType);
          document.getElementById("download-error-div").style.display = "none";
        }

      }
    }, error: function (err) {
      $("#loadMe").modal("hide");
      console.log(err);
      document.getElementById("download-error").innerText = "Error downloading, check whether your popups are blocked.";
      document.getElementById("download-error-div").style.display = "block";
      window.alert("Error downloading, check if you have popups blocked.");
    }
  };
  if (dataPost !== undefined) {
    req.data = dataPost;
    req.type = "POST";
  }

  $.ajax(req)

};