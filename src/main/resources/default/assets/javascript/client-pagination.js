/*
 * Made with all the love in the world
 * by scireum in Remshalden, Germany
 *
 * Copyright by scireum GmbH
 * http://www.scireum.de - info@scireum.de
 */

function paginate(paginationControls, update) {
    const pagination = {
        previousBtn: $("<li><a href='#' aria-label='Previous'><span aria-hidden='true'>&laquo;</span></a></li>"),
        pageIndicator: $("<li><a href='#' aria-label='Page'/></li>"),
        nextBtn: $("<li><a href='#' aria-label='Next'><span aria-hidden='true'>&raquo;</span></a></li>"),
        currentPage: 1
    }

    pagination.reset = function () {
        pagination.updatePage(1)
    };

    pagination.updatePage = function (page) {
        pagination.pageIndicator.children("a").text(page);
        pagination.currentPage = page;
        update(page, pagination);
    }

    pagination.next = function () {
        if (!this.classList.contains("disabled")) {
            pagination.updatePage(pagination.currentPage + 1);
        }
    }

    pagination.prev = function () {
        if (!this.classList.contains("disabled")) {
            pagination.updatePage(pagination.currentPage - 1);
        }
    }

    let paginationList = $("<ul class='pagination'></ul>");
    paginationControls.children().remove();
    paginationList.appendTo(paginationControls);

    paginationList.append(pagination.previousBtn);
    paginationList.append(pagination.pageIndicator);
    paginationList.append(pagination.nextBtn);

    pagination.previousBtn.click(pagination.prev);
    pagination.nextBtn.click(pagination.next);
    pagination.reset();

    return pagination;
}
