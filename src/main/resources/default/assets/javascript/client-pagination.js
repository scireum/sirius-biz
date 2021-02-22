/*
 * Made with all the love in the world
 * by scireum in Remshalden, Germany
 *
 * Copyright by scireum GmbH
 * http://www.scireum.de - info@scireum.de
 */

function Pagination(paginationContainer, pageSize, update) {
    const self = this;

    paginationContainer.innerHTML = "";

    const paginationList = document.createElement("ul");
    paginationList.classList.add("pagination")
    paginationContainer.appendChild(paginationList);

    this.previousBtn = document.createElement("li");
    this.previousBtn.innerHTML = "<a href='#' aria-label='Previous'><span aria-hidden='true'>&#8592;</span></a>";
    this.nextBtn = document.createElement("li");
    this.nextBtn.innerHTML = "<a href='#' aria-label='Next'><span aria-hidden='true'>&#8594;</span></a>";
    this.pageIndicator = document.createElement("li");
    this.pageIndicator.innerHTML = "<a href='#' aria-label='Page'/>";

    paginationList.appendChild(this.previousBtn);
    paginationList.appendChild(this.pageIndicator);
    paginationList.appendChild(this.nextBtn);

    this.previousBtn.addEventListener("click", function () {
        if (!this.classList.contains("disabled")) {
            self.previousPage();
        }
    });
    this.nextBtn.addEventListener("click", function () {
        if (!this.classList.contains("disabled")) {
            self.nextPage();
        }
    });

    this._pageSize = pageSize;
    this._updateFn = update;

    this.reset();
}

Pagination.prototype.reset = function () {
    this.updatePage(0);
}

Pagination.prototype.updatePage = function (page) {
    this.currentPage = page;
    this._updateFn(page, this);
}

Pagination.prototype.setPageLabel = function (label) {
    this.pageIndicator.firstChild.textContent = label;
}

Pagination.prototype.nextPage = function () {
    this.updatePage(this.currentPage + 1);
}

Pagination.prototype.previousPage = function () {
    this.updatePage(this.currentPage - 1);
}
