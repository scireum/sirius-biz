/*
 * Made with all the love in the world
 * by scireum in Remshalden, Germany
 *
 * Copyright by scireum GmbH
 * http://www.scireum.de - info@scireum.de
 */

function Pagination(_paginationContainer, pageSize, update) {
    const self = this;

    _paginationContainer.innerHTML = "";

    const _paginationList = document.createElement("ul");
    _paginationList.classList.add("pagination")
    _paginationContainer.appendChild(_paginationList);

    this._previousBtn = document.createElement("li");
    this._previousBtn.innerHTML = "<a href='#' aria-label='Previous'><span aria-hidden='true'>&#8592;</span></a>";
    this._nextBtn = document.createElement("li");
    this._nextBtn.innerHTML = "<a href='#' aria-label='Next'><span aria-hidden='true'>&#8594;</span></a>";
    this._pageIndicator = document.createElement("li");
    this._pageIndicator.innerHTML = "<a href='#' aria-label='Page'/>";

    _paginationList.appendChild(this._previousBtn);
    _paginationList.appendChild(this._pageIndicator);
    _paginationList.appendChild(this._nextBtn);

    this._previousBtn.addEventListener("click", function () {
        if (!this.classList.contains("disabled")) {
            self.previousPage();
        }
    });
    this._nextBtn.addEventListener("click", function () {
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
    this._pageIndicator.firstChild.textContent = label;
}

Pagination.prototype.nextPage = function () {
    this.updatePage(this.currentPage + 1);
}

Pagination.prototype.previousPage = function () {
    this.updatePage(this.currentPage - 1);
}
