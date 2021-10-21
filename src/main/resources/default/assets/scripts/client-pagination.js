/*
 * Made with all the love in the world
 * by scireum in Remshalden, Germany
 *
 * Copyright by scireum GmbH
 * http://www.scireum.de - info@scireum.de
 */

function Pagination(_paginationContainer, pageSize, update) {
    const self = this;

    _paginationContainer.innerHTML = '';

    const _paginationGroup = document.createElement('div');
    _paginationGroup.classList.add('input-group')
    _paginationGroup.classList.add('justify-content-center')
    _paginationGroup.classList.add('mb-4')
    _paginationContainer.appendChild(_paginationGroup);

    this._previousBtn = document.createElement("div");
    this._previousBtn.classList.add('input-group-prepend');
    this._previousBtn.innerHTML = "<a href='#' class='btn btn-outline-secondary' aria-label='Previous'><span aria-hidden='true'>&#8592;</span></a>";
    this._nextBtn = document.createElement('div');
    this._nextBtn.classList.add('input-group-append');
    this._nextBtn.innerHTML = "<a href='#' class='btn btn-outline-secondary' aria-label='Next'><span aria-hidden='true'>&#8594;</span></a>";
    this._pageIndicator = document.createElement('input');
    this._pageIndicator.classList.add('text-center');
    this._pageIndicator.type = 'text';
    this._pageIndicator.readOnly = true;

    _paginationGroup.appendChild(this._previousBtn);
    _paginationGroup.appendChild(this._pageIndicator);
    _paginationGroup.appendChild(this._nextBtn);

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
    this._pageIndicator.value = label;
}

Pagination.prototype.nextPage = function () {
    this.updatePage(this.currentPage + 1);
}

Pagination.prototype.previousPage = function () {
    this.updatePage(this.currentPage - 1);
}
