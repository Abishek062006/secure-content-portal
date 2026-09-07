(function () {
    var root = document.getElementById('pdf-viewer-root');
    if (!root) {
        return;
    }

    var ticket = root.dataset.ticket;
    var totalPages = parseInt(root.dataset.totalPages, 10) || 1;
    var currentPage = 1;

    var img = document.getElementById('pdf-page-image');
    var loading = document.getElementById('pdf-loading');
    var indicator = document.getElementById('page-indicator');
    var prevBtn = document.getElementById('prev-page');
    var nextBtn = document.getElementById('next-page');

    function updateButtons() {
        prevBtn.disabled = currentPage <= 1;
        nextBtn.disabled = currentPage >= totalPages;
    }

    function loadPage(n) {
        loading.textContent = 'Loading page…';
        loading.hidden = false;
        img.hidden = true;
        indicator.textContent = 'Page ' + n + ' of ' + totalPages;
        updateButtons();
        img.src = '/api/pdf/' + ticket + '/page/' + n;
    }

    img.addEventListener('load', function () {
        loading.hidden = true;
        img.hidden = false;
    });

    img.addEventListener('error', function () {
        loading.textContent = 'Could not load this page. Try again in a moment.';
        loading.hidden = false;
    });

    prevBtn.addEventListener('click', function () {
        if (currentPage > 1) {
            currentPage -= 1;
            loadPage(currentPage);
        }
    });

    nextBtn.addEventListener('click', function () {
        if (currentPage < totalPages) {
            currentPage += 1;
            loadPage(currentPage);
        }
    });

    loadPage(currentPage);
})();
